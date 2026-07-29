package org.oryxos.memory.mem0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.oryxos.core.memory.LongTermMemoryView;
import org.oryxos.core.memory.MemoryScope;
import org.oryxos.memory.LongTermMemoryStore;
import org.oryxos.memory.MemoryStoreSupport;

public final class Mem0MemoryStore implements LongTermMemoryStore {
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    public static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(10);
    private final URI baseUri;
    private final String authorization;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public Mem0MemoryStore(URI baseUri, String credential, ObjectMapper mapper) {
        this.baseUri = validateBaseUri(baseUri);
        this.authorization = credential == null || credential.isBlank()
                ? null : (credential.startsWith("Bearer ")
                        ? credential : "Bearer " + credential);
        this.mapper = java.util.Objects.requireNonNull(mapper);
        this.client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT).build();
    }

    @Override
    public synchronized void append(String content, MemoryScope scope) {
        var value = MemoryStoreSupport.validate(content, scope);
        if (load().stream().anyMatch(item ->
                item.scope() == scope && item.content().equals(value))) {
            return;
        }
        var body = mapper.createObjectNode();
        body.put("user_id", "oryxos");
        body.put("memory", value);
        body.putObject("metadata").put("scope", scope.name());
        send("POST", "v1/memories/", body);
    }

    @Override
    public synchronized List<LongTermMemoryView> load() {
        var root = send("GET", "v1/memories/?user_id=oryxos", null);
        return parse(root);
    }

    @Override
    public synchronized List<LongTermMemoryView> recallByKeyword(String keyword) {
        var value = keyword == null ? "" : keyword.strip();
        if (value.isEmpty()) {
            return load();
        }
        var body = mapper.createObjectNode();
        body.put("user_id", "oryxos");
        body.put("query", value);
        return MemoryStoreSupport.recall(
                parse(send("POST", "v1/memories/search/", body)), value);
    }

    private JsonNode send(String method, String path, JsonNode body) {
        try {
            var builder = HttpRequest.newBuilder(baseUri.resolve(path))
                    .timeout(RESPONSE_TIMEOUT)
                    .header("Accept", "application/json");
            if (authorization != null) {
                builder.header("Authorization", authorization);
            }
            if (body == null) {
                builder.GET();
            } else {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(
                                mapper.writeValueAsString(body)));
            }
            var response = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Mem0 request failed with HTTP " + response.statusCode());
            }
            return response.body().isBlank()
                    ? mapper.createArrayNode() : mapper.readTree(response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Mem0 request interrupted", interrupted);
        } catch (Exception failure) {
            if (failure instanceof IllegalStateException state) {
                throw state;
            }
            throw new IllegalStateException("Mem0 backend unavailable", failure);
        }
    }

    private List<LongTermMemoryView> parse(JsonNode root) {
        var array = root.isArray() ? root
                : root.path("results").isArray() ? root.path("results")
                : root.path("memories");
        var result = new ArrayList<LongTermMemoryView>();
        if (array != null && array.isArray()) {
            for (var node : array) {
                var content = node.path("memory").asText(
                        node.path("content").asText(""));
                if (content.isBlank()) {
                    continue;
                }
                var scopeText = node.path("metadata").path("scope")
                        .asText("ARCHIVAL");
                MemoryScope scope;
                try {
                    scope = MemoryScope.valueOf(scopeText.toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    scope = MemoryScope.ARCHIVAL;
                }
                var id = node.path("id").asText(UUID.nameUUIDFromBytes(
                        (scope + ":" + content).getBytes(
                                java.nio.charset.StandardCharsets.UTF_8)).toString());
                var created = node.path("created_at").asText("");
                result.add(new LongTermMemoryView(id, scope, content,
                        created.isBlank() ? Instant.EPOCH : Instant.parse(created)));
            }
        }
        return List.copyOf(result);
    }

    private static URI validateBaseUri(URI value) {
        if (value == null || value.getHost() == null
                || (!"http".equalsIgnoreCase(value.getScheme())
                && !"https".equalsIgnoreCase(value.getScheme()))) {
            throw new IllegalArgumentException(
                    "Mem0 base URL must be an absolute HTTP(S) URL");
        }
        return URI.create(value.toString().endsWith("/")
                ? value.toString() : value + "/");
    }
}
