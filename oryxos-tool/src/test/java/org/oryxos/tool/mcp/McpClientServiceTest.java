package org.oryxos.tool.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.oryxos.core.model.LlmCallRecord;
import org.oryxos.core.model.ToolDefinition;
import org.oryxos.core.model.ToolInvocationRecord;
import org.oryxos.core.model.ToolResult;
import org.oryxos.core.port.ClockProvider;
import org.oryxos.core.port.InvocationAuditStore;
import org.oryxos.core.tool.ToolExecutionContext;
import org.oryxos.core.tool.ToolExecutor;
import org.oryxos.tool.builtin.HttpGetTool;
import org.oryxos.tool.registry.ToolRegistry;
import org.oryxos.tool.sandbox.WhitelistSandbox;

class McpClientServiceTest {

    @TempDir Path workspace;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void loadsStdioAndHttpConfigAndAdaptsDiscoverySchemaAndResult() throws Exception {
        var file = workspace.resolve("mcp.yaml");
        Files.writeString(file, """
                servers:
                  local:
                    transport: stdio
                    command: demo
                    args: [--stdio]
                  remote:
                    transport: http
                    url: http://localhost/mcp
                """);
        var configs = new McpConfigLoader().load(file);
        assertThat(configs).extracting(McpServerConfiguration::transport)
                .containsExactly(McpServerConfiguration.Transport.STDIO,
                        McpServerConfiguration.Transport.HTTP);

        var schema = mapper.createObjectNode().put("type", "object");
        var service = new McpClientService(List.of(configs.get(0)), ignored ->
                client("search", schema, ToolResult.success("found")));
        var tool = service.tools().getFirst();
        var result = tool.execute(mapper.createObjectNode(), context());

        assertThat(tool.getName()).isEqualTo("search");
        assertThat(tool.getInputSchema()).isEqualTo(schema);
        assertThat(result.content()).isEqualTo("found");
    }

    @Test
    void acceptsDistinctExistingAndCustomServerFixturesWithValidationAndAudit() {
        var existing = new McpServerConfiguration("existing-server",
                McpServerConfiguration.Transport.HTTP, null, List.of(),
                URI.create("http://localhost/existing"), Map.of());
        var custom = new McpServerConfiguration("custom-server",
                McpServerConfiguration.Transport.STDIO, "custom-mcp",
                List.of("--stdio"), null, Map.of());
        var service = new McpClientService(List.of(existing, custom), config -> {
            var argument = config.name().equals("existing-server") ? "query" : "value";
            return client(config.name().replace("-server", "_tool"),
                    objectSchema(argument), ToolResult.success(config.name()));
        });
        var registry = new ToolRegistry(service.tools());

        assertThat(registry.definitions())
                .extracting(ToolDefinition::name, ToolDefinition::origin,
                        ToolDefinition::source)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("existing_tool",
                                ToolDefinition.Origin.MCP, "existing-server"),
                        org.assertj.core.groups.Tuple.tuple("custom_tool",
                                ToolDefinition.Origin.MCP, "custom-server"));
        assertThat(registry.find("existing_tool").orElseThrow().getInputSchema()
                .path("required")).anyMatch(node -> node.asText().equals("query"));
        assertThat(registry.find("custom_tool").orElseThrow().getInputSchema()
                .path("required")).anyMatch(node -> node.asText().equals("value"));

        var audit = new Audit();
        var executor = new ToolExecutor(registry, action -> { }, audit,
                (ClockProvider) Instant::now, mapper, 0);
        assertThat(executor.execute("existing_tool", "{\"query\":\"weather\"}",
                context()).content()).isEqualTo("existing-server");
        assertThat(executor.execute("custom_tool", "{\"value\":\"digest\"}",
                context()).content()).isEqualTo("custom-server");
        assertThat(executor.execute("custom_tool", "{}", context()).success())
                .isFalse();

        assertThat(audit.tools).hasSize(3);
        assertThat(audit.tools).extracting(ToolInvocationRecord::toolName)
                .containsExactly("existing_tool", "custom_tool", "custom_tool");
        assertThat(audit.tools.getLast().errorCode()).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void rejectsDuplicateToolsAndNormalizesTransportFailure() {
        var configs = List.of(
                new McpServerConfiguration("one", McpServerConfiguration.Transport.HTTP,
                        null, List.of(), URI.create("http://localhost/one"), Map.of()),
                new McpServerConfiguration("two", McpServerConfiguration.Transport.HTTP,
                        null, List.of(), URI.create("http://localhost/two"), Map.of()));
        var schema = mapper.createObjectNode().put("type", "object");
        assertThatThrownBy(() -> new McpClientService(configs,
                ignored -> client("same", schema, ToolResult.success("ok"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate MCP Tool");
        assertThatThrownBy(() -> new McpClientService(
                List.of(configs.getFirst(), configs.getFirst()),
                ignored -> client("unique-" + System.nanoTime(), schema,
                        ToolResult.success("ok"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate MCP server");

        var service = new McpClientService(List.of(configs.getFirst()), ignored ->
                new McpClientService.Client() {
                    @Override public List<McpClientService.RemoteTool> listTools() {
                        return List.of(new McpClientService.RemoteTool(
                                "broken", "broken", schema));
                    }
                    @Override public ToolResult call(String name,
                            com.fasterxml.jackson.databind.JsonNode arguments) {
                        throw new IllegalStateException("transport down");
                    }
                });
        var result = service.tools().getFirst().execute(
                mapper.createObjectNode(), context());
        assertThat(result.success()).isFalse();
        assertThat(result.retryable()).isTrue();
    }

    @Test
    void reportsStdioStartupAndHttpDiscoveryTransportFailures() throws Exception {
        var sandbox = new WhitelistSandbox(java.util.Set.of("localhost"),
                java.util.Set.of("definitely-missing-mcp-command"),
                java.util.Set.of(workspace));
        var factory = new DefaultMcpClientFactory(
                HttpGetTool.safeClient(), mapper, sandbox);
        var stdio = new McpServerConfiguration("stdio",
                McpServerConfiguration.Transport.STDIO,
                "definitely-missing-mcp-command", List.of(), null, Map.of());
        assertThatThrownBy(() -> factory.connect(stdio))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stdio");

        var server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        try {
            var http = new McpServerConfiguration("http",
                    McpServerConfiguration.Transport.HTTP, null, List.of(),
                    URI.create("http://localhost:" + server.getAddress().getPort()),
                    Map.of());
            assertThatThrownBy(() -> new McpClientService(List.of(http), factory))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MCP HTTP status");
        } finally {
            server.stop(0);
        }
    }

    private McpClientService.Client client(String name,
            com.fasterxml.jackson.databind.JsonNode schema, ToolResult result) {
        return new McpClientService.Client() {
            @Override public List<McpClientService.RemoteTool> listTools() {
                return List.of(new McpClientService.RemoteTool(name, "description", schema));
            }
            @Override public ToolResult call(String tool,
                    com.fasterxml.jackson.databind.JsonNode arguments) {
                return result;
            }
        };
    }

    private com.fasterxml.jackson.databind.JsonNode objectSchema(String required) {
        var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.putObject("properties").putObject(required).put("type", "string");
        schema.putArray("required").add(required);
        return schema;
    }

    private ToolExecutionContext context() {
        return new ToolExecutionContext("session", "agent", workspace,
                Instant.now().plusSeconds(5), "test", "user", Map.of());
    }

    private static final class Audit implements InvocationAuditStore {
        private final List<ToolInvocationRecord> tools = new ArrayList<>();
        @Override public void saveLlmCall(LlmCallRecord record) { }
        @Override public void saveToolInvocation(ToolInvocationRecord record) {
            tools.add(record);
        }
        @Override public Page<LlmCallRecord> findLlmCalls(
                String sessionId, int offset, int limit) {
            return new Page<>(List.of(), offset, limit, 0);
        }
        @Override public Page<ToolInvocationRecord> findToolInvocations(
                String sessionId, int offset, int limit) {
            return new Page<>(tools, offset, limit, tools.size());
        }
    }
}
