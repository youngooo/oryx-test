package org.oryxos.channel.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.oryxos.core.agent.ContextLoader;
import org.oryxos.core.memory.MemoryContext;
import org.oryxos.core.memory.MemoryScope;
import org.oryxos.core.model.AgentDefinition;
import org.oryxos.core.model.LlmCallRecord;
import org.oryxos.core.model.Message;
import org.oryxos.core.model.Profile;
import org.oryxos.core.model.Session;
import org.oryxos.core.model.ToolInvocationRecord;
import org.oryxos.core.model.ToolResult;
import org.oryxos.core.port.InvocationAuditStore;
import org.oryxos.core.port.MemoryService;
import org.oryxos.core.port.ProviderGateway;
import org.oryxos.core.port.SessionStore;
import org.oryxos.core.port.ToolCatalog;
import org.oryxos.core.prompt.PromptBuilder;
import org.oryxos.core.react.ReActLoop;
import org.oryxos.core.service.AgentService;
import org.oryxos.core.tool.OryxTool;
import org.oryxos.core.tool.ToolExecutionContext;
import org.oryxos.core.tool.ToolExecutor;

class CliChannelAcceptanceTest {

    @TempDir Path workspace;

    @Test
    void runsNoToolConversation() {
        var channel = channel(10, null,
                new ProviderGateway.Response("北京今天晴朗，建议穿轻薄短袖。",
                        List.of(), null, null, "stop"));
        var output = new StringWriter();

        var code = channel.chat("weather", "user",
                new StringReader("北京的天气怎么样？\n/exit\n"), output);

        assertThat(code).isZero();
        assertThat(output.toString())
                .isEqualTo("北京今天晴朗，建议穿轻薄短袖。" + System.lineSeparator());
    }

    @Test
    void runsHttpToolRoundAndReturnsFinalAdvice() {
        var tool = weatherTool();
        var channel = channel(10, tool,
                new ProviderGateway.Response(null,
                        List.of(new ProviderGateway.ToolCall("weather-1",
                                "http_get",
                                "{\"url\":\"https://weather.test/today\"}")),
                        null, null, "tool_calls"),
                new ProviderGateway.Response("Wear a waterproof jacket.",
                        List.of(), null, null, "stop"));
        var output = new StringWriter();

        var code = channel.chat("weather", "user",
                new StringReader("What should I wear?\n"), output);

        assertThat(code).isZero();
        assertThat(output.toString()).contains("waterproof jacket");
    }

    @Test
    void reportsMaximumIterationAsRuntimeFailure() {
        var tool = weatherTool();
        var channel = channel(1, tool,
                new ProviderGateway.Response(null,
                        List.of(new ProviderGateway.ToolCall("weather-1",
                                "http_get",
                                "{\"url\":\"https://weather.test/today\"}")),
                        null, null, "tool_calls"));
        var output = new StringWriter();

        var code = channel.chat("weather", "user",
                new StringReader("loop\n"), output);

        assertThat(code).isEqualTo(5);
        assertThat(output.toString()).contains("Maximum ReAct iterations");
    }

    private CliChannel channel(int maxIterations, OryxTool tool,
            ProviderGateway.Response... responses) {
        var store = new Store();
        var catalog = new Catalog(tool);
        var profile = new Profile("weather", "deepseek", "deepseek-chat",
                tool == null ? Set.of() : Set.of(tool.getName()),
                Set.of("cli"), null, List.of(), maxIterations, null);
        var agent = new AgentDefinition("weather",
                workspace.resolve("AGENT.md"), "Weather assistant", profile,
                null, null, null, AgentDefinition.LoadStatus.VALID, List.of());
        var prompt = new PromptBuilder(new ContextLoader(workspace),
                store, catalog, Instant::now, ZoneId.of("UTC"));
        var queue = new ArrayDeque<>(List.of(responses));
        ProviderGateway provider = request -> queue.removeFirst();
        var executor = new ToolExecutor(catalog, action -> { }, new Audit(),
                Instant::now, new ObjectMapper(), 0);
        var loop = new ReActLoop(prompt, provider, executor, store,
                Instant::now, Duration.ofSeconds(5));
        return new CliChannel(new AgentService(Map.of("weather", agent),
                store, loop, Instant::now, workspace));
    }

    private OryxTool weatherTool() {
        var schema = new ObjectMapper().createObjectNode();
        ((com.fasterxml.jackson.databind.node.ObjectNode) schema)
                .put("type", "object").put("additionalProperties", false);
        ((com.fasterxml.jackson.databind.node.ObjectNode) schema)
                .putObject("properties").putObject("url").put("type", "string");
        ((com.fasterxml.jackson.databind.node.ObjectNode) schema)
                .putArray("required").add("url");
        return new OryxTool() {
            @Override public String getName() { return "http_get"; }
            @Override public String getDescription() { return "Get weather"; }
            @Override public JsonNode getInputSchema() { return schema; }
            @Override public ToolResult execute(JsonNode arguments,
                    ToolExecutionContext context) {
                return ToolResult.success(
                        "{\"temperature\":18,\"condition\":\"rain\"}");
            }
        };
    }

    private record Catalog(OryxTool tool) implements ToolCatalog {
        @Override public Optional<OryxTool> find(String name) {
            return tool != null && tool.getName().equals(name)
                    ? Optional.of(tool) : Optional.empty();
        }
        @Override public List<OryxTool> availableTo(Set<String> names) {
            return tool != null && names.contains(tool.getName())
                    ? List.of(tool) : List.of();
        }
    }

    private static class Store implements SessionStore, MemoryService {
        private final Map<String, Session> sessions = new LinkedHashMap<>();
        @Override public synchronized Session save(Session session) {
            sessions.put(session.sessionId(), session);
            return session;
        }
        @Override public synchronized Optional<Session> findById(String id) {
            return Optional.ofNullable(sessions.get(id));
        }
        @Override public synchronized Optional<Session> findActive(
                String profile, String channel, String user) {
            return sessions.values().stream().filter(value ->
                    value.profileName().equals(profile)
                            && value.channel().equals(channel)
                            && value.userId().equals(user)).findFirst();
        }
        @Override public synchronized Session append(String id, Message message) {
            var updated = sessions.get(id).append(message);
            sessions.put(id, updated);
            return updated;
        }
        @Override public synchronized MemoryContext loadContext(
                String id, Profile profile) {
            return new MemoryContext(sessions.get(id).messages(), List.of(), "");
        }
        @Override public synchronized Session appendSessionMessage(
                String id, Message message) {
            return append(id, message);
        }
        @Override public void remember(String content, MemoryScope scope) { }
    }

    private static class Audit implements InvocationAuditStore {
        final List<ToolInvocationRecord> tools = new ArrayList<>();
        @Override public void saveLlmCall(LlmCallRecord record) { }
        @Override public void saveToolInvocation(ToolInvocationRecord record) {
            tools.add(record);
        }
        @Override public Page<LlmCallRecord> findLlmCalls(
                String id, int offset, int limit) {
            return new Page<>(List.of(), offset, limit, 0);
        }
        @Override public Page<ToolInvocationRecord> findToolInvocations(
                String id, int offset, int limit) {
            return new Page<>(tools, offset, limit, tools.size());
        }
    }
}
