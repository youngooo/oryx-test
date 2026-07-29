package org.oryxos.tool.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.oryxos.core.model.ToolResult;
import org.oryxos.core.port.Sandbox;

public final class DefaultMcpClientFactory implements McpClientService.ClientFactory {

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final Sandbox sandbox;

    public DefaultMcpClientFactory(HttpClient http, ObjectMapper mapper,
            Sandbox sandbox) {
        this.http = java.util.Objects.requireNonNull(http);
        this.mapper = java.util.Objects.requireNonNull(mapper);
        this.sandbox = java.util.Objects.requireNonNull(sandbox);
    }

    @Override
    public McpClientService.Client connect(McpServerConfiguration config) {
        return config.transport() == McpServerConfiguration.Transport.HTTP
                ? new HttpClientAdapter(config) : new StdioClientAdapter(config);
    }

    private abstract class JsonRpcClient implements McpClientService.Client {
        private final AtomicLong ids = new AtomicLong();

        abstract JsonNode exchange(JsonNode request) throws Exception;

        @Override
        public java.util.List<McpClientService.RemoteTool> listTools() {
            var response = callRpc("tools/list", mapper.createObjectNode());
            var tools = new ArrayList<McpClientService.RemoteTool>();
            response.path("tools").forEach(tool -> tools.add(
                    new McpClientService.RemoteTool(tool.path("name").asText(),
                            tool.path("description").asText("MCP Tool"),
                            tool.path("inputSchema"))));
            return List.copyOf(tools);
        }

        @Override
        public ToolResult call(String name, JsonNode arguments) {
            try {
                var params = mapper.createObjectNode().put("name", name);
                params.set("arguments", arguments);
                var result = callRpc("tools/call", params);
                if (result.path("isError").asBoolean(false)) {
                    return ToolResult.failure(content(result), false);
                }
                return ToolResult.success(content(result));
            } catch (RuntimeException failure) {
                return ToolResult.failure("MCP transport failed: "
                        + failure.getClass().getSimpleName(), true);
            }
        }

        private JsonNode callRpc(String method, JsonNode params) {
            try {
                var request = mapper.createObjectNode()
                        .put("jsonrpc", "2.0")
                        .put("id", ids.incrementAndGet())
                        .put("method", method);
                request.set("params", params);
                var response = exchange(request);
                if (response.has("error")) {
                    throw new IllegalStateException("MCP error: "
                            + response.path("error").path("message").asText("unknown"));
                }
                return response.path("result");
            } catch (Exception failure) {
                throw failure instanceof RuntimeException runtime
                        ? runtime : new IllegalStateException("MCP transport failed", failure);
            }
        }

        private String content(JsonNode result) {
            var parts = new ArrayList<String>();
            result.path("content").forEach(item -> {
                if ("text".equals(item.path("type").asText()) || item.has("text")) {
                    parts.add(item.path("text").asText());
                }
            });
            return parts.isEmpty() ? "(empty MCP result)" : String.join("\n", parts);
        }
    }

    private final class HttpClientAdapter extends JsonRpcClient {
        private final McpServerConfiguration config;

        private HttpClientAdapter(McpServerConfiguration config) {
            this.config = config;
            sandbox.enforce(new Sandbox.Action("HTTP", config.url().toString()));
        }

        @Override
        JsonNode exchange(JsonNode request) throws Exception {
            sandbox.enforce(new Sandbox.Action("HTTP", config.url().toString()));
            var builder = HttpRequest.newBuilder(config.url())
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json");
            config.headers().forEach(builder::header);
            var response = http.send(builder.POST(HttpRequest.BodyPublishers.ofString(
                    mapper.writeValueAsString(request))).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("MCP HTTP status " + response.statusCode());
            }
            return mapper.readTree(response.body());
        }
    }

    private final class StdioClientAdapter extends JsonRpcClient {
        private final Process process;
        private final BufferedReader reader;
        private final BufferedWriter writer;

        private StdioClientAdapter(McpServerConfiguration config) {
            try {
                sandbox.enforce(new Sandbox.Action("SHELL", config.command()));
                var command = new ArrayList<String>();
                command.add(config.command());
                command.addAll(config.args());
                process = new ProcessBuilder(command).redirectError(
                        ProcessBuilder.Redirect.INHERIT).start();
                reader = new BufferedReader(new InputStreamReader(
                        process.getInputStream(), StandardCharsets.UTF_8));
                writer = new BufferedWriter(new OutputStreamWriter(
                        process.getOutputStream(), StandardCharsets.UTF_8));
            } catch (Exception failure) {
                throw new IllegalStateException("Unable to start MCP stdio server", failure);
            }
        }

        @Override
        synchronized JsonNode exchange(JsonNode request) throws Exception {
            writer.write(mapper.writeValueAsString(request));
            writer.newLine();
            writer.flush();
            var line = reader.readLine();
            if (line == null) {
                throw new IllegalStateException("MCP stdio server closed");
            }
            return mapper.readTree(line);
        }

        @Override
        public void close() {
            process.destroy();
        }
    }
}
