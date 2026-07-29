package org.oryxos.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.oryxos.core.model.ToolResult;
import org.oryxos.core.port.Sandbox;
import org.oryxos.core.tool.OryxTool;
import org.oryxos.core.tool.ToolExecutionContext;

public final class HttpGetTool implements OryxTool {

    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_RESULT_CHARS = 64 * 1024;
    private static final Set<String> FORBIDDEN_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie");

    private final HttpClient httpClient;
    private final Sandbox sandbox;
    private final ObjectNode schema;

    public HttpGetTool(HttpClient httpClient, Sandbox sandbox, ObjectMapper mapper) {
        this.httpClient = java.util.Objects.requireNonNull(httpClient);
        this.sandbox = java.util.Objects.requireNonNull(sandbox);
        this.schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        var properties = schema.putObject("properties");
        properties.putObject("url").put("type", "string");
        properties.putObject("headers").put("type", "object");
        properties.putObject("timeoutSeconds").put("type", "integer");
        schema.putArray("required").add("url");
    }

    public static HttpClient safeClient() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public String getName() {
        return "http_get";
    }

    @Override
    public String getDescription() {
        return "Fetch an allowlisted HTTP(S) URL";
    }

    @Override
    public JsonNode getInputSchema() {
        return schema.deepCopy();
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolExecutionContext context) {
        try {
            var uri = URI.create(arguments.path("url").asText());
            var timeoutSeconds = arguments.path("timeoutSeconds").asInt(10);
            if (timeoutSeconds < 1 || timeoutSeconds > 30) {
                return ToolResult.failure(
                        "timeoutSeconds must be between 1 and 30", false);
            }
            for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
                sandbox.enforce(new Sandbox.Action("HTTP", uri.toString()));
                var builder = HttpRequest.newBuilder(uri)
                        .GET().timeout(Duration.ofSeconds(timeoutSeconds));
                var headers = arguments.path("headers");
                if (headers.isObject()) {
                    var entries = headers.fields();
                    while (entries.hasNext()) {
                        var entry = entries.next();
                        if (FORBIDDEN_HEADERS.contains(
                                entry.getKey().toLowerCase(Locale.ROOT))) {
                            return ToolResult.failure(
                                    "Secret-bearing headers are not accepted", false);
                        }
                        if (!entry.getValue().isTextual()) {
                            return ToolResult.failure(
                                    "HTTP header values must be strings", false);
                        }
                        builder.header(entry.getKey(), entry.getValue().asText());
                    }
                }
                var response = httpClient.send(builder.build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 300 && response.statusCode() < 400) {
                    var location = response.headers().firstValue("location");
                    if (location.isEmpty()) {
                        return ToolResult.failure(
                                "Redirect response omitted Location", false);
                    }
                    if (redirect == MAX_REDIRECTS) {
                        return ToolResult.failure("Too many HTTP redirects", false);
                    }
                    uri = uri.resolve(location.get());
                    continue;
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    return ToolResult.failure(
                            "HTTP request failed with status " + response.statusCode(),
                            response.statusCode() >= 500);
                }
                var body = response.body() == null ? "" : response.body();
                if (body.length() > MAX_RESULT_CHARS) {
                    body = body.substring(0, MAX_RESULT_CHARS)
                            + "\n[truncated]";
                }
                return ToolResult.success(body.isBlank() ? "(empty response)" : body);
            }
            return ToolResult.failure("HTTP redirect handling failed", false);
        } catch (java.net.http.HttpTimeoutException timeout) {
            return ToolResult.failure("HTTP request timed out", true);
        } catch (Sandbox.DeniedException denied) {
            return ToolResult.failure(denied.getMessage(), false);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return ToolResult.failure("HTTP request interrupted", true);
        } catch (Exception failure) {
            return ToolResult.failure("HTTP request failed: "
                    + failure.getClass().getSimpleName(), false);
        }
    }
}
