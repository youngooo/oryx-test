package org.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.oryxos.core.memory.MemoryContext;
import org.oryxos.core.memory.MemoryScope;
import org.oryxos.core.model.LlmCallRecord;
import org.oryxos.core.model.Message;
import org.oryxos.core.model.Profile;
import org.oryxos.core.model.Session;
import org.oryxos.core.model.ToolDefinition;
import org.oryxos.core.model.ToolInvocationRecord;
import org.oryxos.core.model.ToolResult;
import org.oryxos.core.port.ClockProvider;
import org.oryxos.core.port.InvocationAuditStore;
import org.oryxos.core.port.MemoryService;
import org.oryxos.core.tool.ToolExecutionContext;
import org.oryxos.memory.tool.MemoryTools;
import org.oryxos.tool.builtin.HttpGetTool;
import org.oryxos.tool.builtin.HttpPostTool;
import org.oryxos.tool.builtin.ListDirTool;
import org.oryxos.tool.builtin.ReadFileTool;
import org.oryxos.tool.builtin.ShellTool;
import org.oryxos.tool.builtin.WriteFileTool;
import org.oryxos.tool.mcp.McpClientService;
import org.oryxos.tool.mcp.McpServerConfiguration;
import org.oryxos.tool.notify.NotifyChannelAdapter;
import org.oryxos.tool.notify.NotifyTool;
import org.oryxos.tool.plugin.JavaPluginToolAdapter;
import org.oryxos.tool.registry.ToolRegistry;
import org.oryxos.tool.sandbox.WhitelistSandbox;
import org.springframework.ai.tool.annotation.Tool;

class ToolAcceptanceTest {

    @TempDir Path workspace;
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            if (exchange.getRequestURI().getPath().equals("/failure")) {
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
                return;
            }
            var body = "ok".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach void stopServer() { server.stop(0); }

    @Test
    void catalogsAllNineBuiltinsAndRoutesEveryOriginThroughOneAuditedExecutor()
            throws Exception {
        var sandbox = new WhitelistSandbox(Set.of("localhost"),
                Set.of(javaExecutableName()), Set.of(workspace));
        var registry = registry(sandbox);

        assertThat(registry.definitions()).filteredOn(definition ->
                definition.origin() == ToolDefinition.Origin.BUILT_IN)
                .extracting(ToolDefinition::name)
                .containsExactlyInAnyOrder("read_file", "write_file", "list_dir",
                        "shell", "http_get", "http_post", "save_memory",
                        "recall_memory", "notify");
        assertThat(registry.definitions()).extracting(ToolDefinition::origin)
                .contains(ToolDefinition.Origin.BUILT_IN,
                        ToolDefinition.Origin.MCP,
                        ToolDefinition.Origin.JAVA_PLUGIN);
        assertThat(registry.definitions()).filteredOn(definition ->
                definition.origin() == ToolDefinition.Origin.MCP)
                .extracting(ToolDefinition::name, ToolDefinition::source)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                                "existing_mcp", "existing-server"),
                        org.assertj.core.groups.Tuple.tuple(
                                "custom_mcp", "custom-server"));

        var audit = new Audit();
        var executor = new org.oryxos.core.tool.ToolExecutor(registry, sandbox,
                audit, (ClockProvider) Instant::now, mapper, 0);
        java.nio.file.Files.writeString(workspace.resolve("read.txt"), "text");
        execute(executor, "read_file", "{\"path\":\"read.txt\"}");
        execute(executor, "write_file",
                "{\"path\":\"write.txt\",\"content\":\"text\"}");
        execute(executor, "list_dir", "{\"path\":\".\"}");
        execute(executor, "shell", mapper.writeValueAsString(Map.of(
                "command", javaExecutable(), "args", List.of("-version"))));
        execute(executor, "http_get", "{\"url\":\"" + baseUrl + "\"}");
        execute(executor, "http_post", "{\"url\":\"" + baseUrl + "\",\"body\":\"{}\"}");
        execute(executor, "save_memory", "{\"content\":\"fact\"}");
        execute(executor, "recall_memory", "{}");
        execute(executor, "notify", "{\"message\":\"done\",\"target\":\"daily\"}");
        execute(executor, "existing_mcp", "{\"value\":\"ok\"}");
        execute(executor, "custom_mcp", "{\"value\":\"ok\"}");
        var plugin = registry.find("plugin_ping").orElseThrow();
        var parameter = plugin.getInputSchema().path("properties").fieldNames().next();
        execute(executor, "plugin_ping", "{\"" + parameter + "\":\"hi\"}");

        assertThat(audit.tools).hasSize(12).allMatch(ToolInvocationRecord::success);
    }

    @Test
    void rejectsInvalidArgumentsForAllBuiltinsAndEveryPluginRoute() {
        var sandbox = new WhitelistSandbox(Set.of("localhost"),
                Set.of(javaExecutableName()), Set.of(workspace));
        var audit = new Audit();
        var executor = new org.oryxos.core.tool.ToolExecutor(registry(sandbox),
                sandbox, audit, (ClockProvider) Instant::now, mapper, 0);
        var requiredArguments = List.of("read_file", "write_file", "list_dir",
                "shell", "http_get", "http_post", "save_memory", "notify",
                "existing_mcp", "custom_mcp", "plugin_ping");
        requiredArguments.forEach(name ->
                assertThat(executor.execute(name, "{}", context()).success())
                        .as(name).isFalse());
        assertThat(executor.execute("recall_memory",
                "{\"unexpected\":true}", context()).success()).isFalse();

        assertThat(audit.tools).hasSize(12);
        assertThat(audit.tools).extracting(ToolInvocationRecord::errorCode)
                .containsOnly("INVALID_ARGUMENT");
    }

    @Test
    void normalizesRemoteAndPluginFailuresAndAuditsEveryRoute() {
        var sandbox = new WhitelistSandbox(Set.of("localhost"),
                Set.of(javaExecutableName()), Set.of(workspace));
        var audit = new Audit();
        var executor = new org.oryxos.core.tool.ToolExecutor(registry(sandbox),
                sandbox, audit, (ClockProvider) Instant::now, mapper, 0);

        assertThat(executor.execute("existing_mcp",
                "{\"value\":\"fail\"}", context()).retryable()).isTrue();
        assertThat(executor.execute("custom_mcp",
                "{\"value\":\"fail\"}", context()).retryable()).isTrue();
        assertThat(executor.execute("plugin_ping",
                "{\"value\":\"fail\"}", context()).retryable()).isFalse();
        assertThat(executor.execute("http_get",
                "{\"url\":\"" + baseUrl + "/failure\"}", context()).retryable())
                .isTrue();
        assertThat(executor.execute("notify",
                "{\"message\":\"done\",\"target\":\"fail\"}", context()).retryable())
                .isTrue();

        assertThat(audit.tools).hasSize(5);
        assertThat(audit.tools).extracting(ToolInvocationRecord::errorCode)
                .containsExactly("RETRY_EXHAUSTED", "RETRY_EXHAUSTED",
                        "TOOL_ERROR", "RETRY_EXHAUSTED", "RETRY_EXHAUSTED");
    }

    @Test
    void auditsWorkspaceCommandAndParsedHostSandboxRejections() {
        var sandbox = new WhitelistSandbox(Set.of("localhost"),
                Set.of(javaExecutableName()), Set.of(workspace));
        var audit = new Audit();
        var executor = new org.oryxos.core.tool.ToolExecutor(registry(sandbox),
                sandbox, audit, (ClockProvider) Instant::now, mapper, 0);

        assertThat(executor.execute("read_file",
                "{\"path\":\"../outside.txt\"}", context()).success()).isFalse();
        assertThat(executor.execute("shell",
                "{\"command\":\"denied-command\",\"args\":[]}", context())
                .success()).isFalse();
        assertThat(executor.execute("http_get",
                "{\"url\":\"https://example.invalid\"}", context()).success())
                .isFalse();

        assertThat(audit.tools).hasSize(3);
        assertThat(audit.tools).extracting(ToolInvocationRecord::errorCode)
                .containsOnly("SANDBOX_DENIED");
    }

    private void execute(org.oryxos.core.tool.ToolExecutor executor,
            String name, String json) {
        assertThat(executor.execute(name, json, context()).success())
                .as(name).isTrue();
    }

    private ToolExecutionContext context() {
        return new ToolExecutionContext("session", "agent", workspace,
                Instant.now().plusSeconds(20), "test", "user", Map.of());
    }

    private ToolRegistry registry(WhitelistSandbox sandbox) {
        var tools = new ArrayList<org.oryxos.core.tool.OryxTool>();
        tools.add(new ReadFileTool(mapper));
        tools.add(new WriteFileTool(mapper));
        tools.add(new ListDirTool(mapper));
        tools.add(new ShellTool(mapper));
        tools.add(new HttpGetTool(HttpGetTool.safeClient(), sandbox, mapper));
        tools.add(new HttpPostTool(HttpGetTool.safeClient(), sandbox, mapper));
        tools.addAll(MemoryTools.create(memory(), mapper));
        tools.add(new NotifyTool(List.of(new NotifyChannelAdapter() {
            @Override public String channel() { return "test"; }
            @Override public ToolResult send(String target, String title, String message) {
                return target.equals("fail")
                        ? ToolResult.failure("notification unavailable", true)
                        : ToolResult.success("sent");
            }
        }), "test", mapper));
        var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.putObject("properties").putObject("value").put("type", "string");
        schema.putArray("required").add("value");
        var configurations = List.of(
                new McpServerConfiguration("existing-server",
                        McpServerConfiguration.Transport.HTTP, null, List.of(),
                        java.net.URI.create(baseUrl + "/existing"), Map.of()),
                new McpServerConfiguration("custom-server",
                        McpServerConfiguration.Transport.STDIO, "custom-mcp",
                        List.of("--stdio"), null, Map.of()));
        tools.addAll(new McpClientService(configurations, config ->
                new McpClientService.Client() {
                    @Override public List<McpClientService.RemoteTool> listTools() {
                        var name = config.name().equals("existing-server")
                                ? "existing_mcp" : "custom_mcp";
                        return List.of(new McpClientService.RemoteTool(
                                name, config.name(), schema));
                    }
                    @Override public ToolResult call(String name,
                            com.fasterxml.jackson.databind.JsonNode arguments) {
                        if ("fail".equals(arguments.path("value").asText())) {
                            throw new IllegalStateException("transport-secret");
                        }
                        return ToolResult.success(config.name());
                    }
                }).tools());
        tools.addAll(JavaPluginToolAdapter.discover(mapper, List.of(new Plugin())));
        return new ToolRegistry(tools);
    }

    private MemoryService memory() {
        return new MemoryService() {
            @Override public MemoryContext loadContext(String id, Profile profile) {
                return new MemoryContext(List.of(), List.of(), "");
            }
            @Override public Session appendSessionMessage(String id, Message message) {
                throw new UnsupportedOperationException();
            }
            @Override public void remember(String content, MemoryScope scope) { }
        };
    }

    private String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win")
                        ? "java.exe" : "java").toString();
    }

    private String javaExecutableName() {
        return Path.of(javaExecutable()).getFileName().toString();
    }

    static final class Plugin {
        @Tool(name = "plugin_ping", description = "Plugin ping")
        String ping(String value) {
            if ("fail".equals(value)) {
                throw new IllegalStateException("plugin-secret");
            }
            return value;
        }
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
