package org.oryxos.core.react;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.oryxos.core.tool.OryxTool;
import org.oryxos.core.tool.ToolExecutionContext;
import org.oryxos.core.tool.ToolExecutor;

class ReActLoopTest {

    @TempDir Path workspace;

    @Test
    void dependsOnUnifiedMemoryFacadeInsteadOfSessionPersistence() {
        assertThat(List.of(ReActLoop.class.getDeclaredFields()).stream()
                .map(java.lang.reflect.Field::getType))
                .contains(MemoryService.class)
                .doesNotContain(SessionStore.class);
        assertThat(List.of(ReActLoop.class.getDeclaredConstructors()).stream()
                .flatMap(constructor -> List.of(
                        constructor.getParameterTypes()).stream()))
                .contains(MemoryService.class)
                .doesNotContain(SessionStore.class);
    }

    @Test
    void terminatesWithoutToolAndAppendsFinalResponse() {
        var fixture = fixture(10, new ProviderGateway.Response(
                "It is sunny.", List.of(), null, null, "stop"));

        var result = fixture.loop.run(fixture.agent, fixture.session, workspace);

        assertThat(result.terminationReason())
                .isEqualTo(ReActLoop.TerminationReason.FINAL_RESPONSE);
        assertThat(result.iterations()).isEqualTo(1);
        assertThat(result.session().messages()).extracting(Message::content)
                .containsExactly("It is sunny.");
    }

    @Test
    void executesSequentialMultiCallsAndMultipleRoundsInOrder() {
        var first = new ProviderGateway.Response("Checking two sources",
                List.of(call("c1"), call("c2")), null, null, "tool_calls");
        var second = new ProviderGateway.Response(null,
                List.of(call("c3")), null, null, "tool_calls");
        var third = new ProviderGateway.Response("Wear a light jacket.",
                List.of(), null, null, "stop");
        var fixture = fixture(10, first, second, third);

        var result = fixture.loop.run(fixture.agent, fixture.session, workspace);

        assertThat(result.terminationReason())
                .isEqualTo(ReActLoop.TerminationReason.FINAL_RESPONSE);
        assertThat(fixture.executed).containsExactly("c1", "c2", "c3");
        assertThat(result.session().messages()).extracting(Message::role)
                .containsExactly(Message.Role.ASSISTANT, Message.Role.TOOL,
                        Message.Role.TOOL, Message.Role.ASSISTANT,
                        Message.Role.TOOL, Message.Role.ASSISTANT);
    }

    @Test
    void reportsMalformedResponseToolFailureAndMaximumIteration() {
        var malformed = fixture(10,
                new ProviderGateway.Response(null, List.of(), null, null, null));
        assertThat(malformed.loop.run(
                malformed.agent, malformed.session, workspace).terminationReason())
                .isEqualTo(ReActLoop.TerminationReason.PROVIDER_ERROR);

        var failing = fixture(10,
                new ProviderGateway.Response(null,
                        List.of(new ProviderGateway.ToolCall(
                                "fail", "unknown", "{}")),
                        null, null, "tool_calls"));
        assertThat(failing.loop.run(
                failing.agent, failing.session, workspace).terminationReason())
                .isEqualTo(ReActLoop.TerminationReason.TOOL_ERROR);

        var maximum = fixture(2,
                new ProviderGateway.Response(null, List.of(call("c1")),
                        null, null, "tool_calls"),
                new ProviderGateway.Response(null, List.of(call("c2")),
                        null, null, "tool_calls"));
        var result = maximum.loop.run(maximum.agent, maximum.session, workspace);
        assertThat(result.terminationReason())
                .isEqualTo(ReActLoop.TerminationReason.MAX_ITERATIONS);
        assertThat(result.iterations()).isEqualTo(2);
    }

    private Fixture fixture(int maxIterations,
            ProviderGateway.Response... responses) {
        var store = new MemorySessionStore();
        var now = Instant.parse("2026-07-29T00:00:00Z");
        var session = store.save(new Session("session-1", "weather", "cli",
                "user", List.of(), Session.Status.ACTIVE, now, now, null, 0));
        var profile = new Profile("weather", "deepseek", "deepseek-chat",
                Set.of("http_get"), Set.of("cli"), null, List.of(),
                maxIterations, null);
        var agent = new AgentDefinition("weather",
                workspace.resolve("AGENT.md"), "Weather assistant", profile,
                null, null, null, AgentDefinition.LoadStatus.VALID, List.of());
        var executed = new ArrayList<String>();
        var tool = tool(executed);
        var catalog = new Catalog(tool);
        var audit = new Audit();
        var executor = new ToolExecutor(catalog, action -> { }, audit,
                Instant::now, new ObjectMapper(), 0);
        var prompt = new PromptBuilder(new ContextLoader(workspace),
                store, catalog, Instant::now, ZoneId.of("UTC"));
        var queue = new ArrayDeque<>(List.of(responses));
        ProviderGateway provider = request -> queue.removeFirst();
        var loop = new ReActLoop(prompt, provider, executor, store,
                Instant::now, Duration.ofSeconds(10));
        return new Fixture(loop, agent, session, executed);
    }

    private ProviderGateway.ToolCall call(String id) {
        return new ProviderGateway.ToolCall(id, "http_get",
                "{\"url\":\"https://weather.test/" + id + "\"}");
    }

    private OryxTool tool(List<String> executed) {
        var mapper = new ObjectMapper();
        var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.putObject("properties").putObject("url").put("type", "string");
        schema.putArray("required").add("url");
        return new OryxTool() {
            @Override public String getName() { return "http_get"; }
            @Override public String getDescription() { return "weather"; }
            @Override public JsonNode getInputSchema() { return schema; }
            @Override public ToolResult execute(JsonNode arguments,
                    ToolExecutionContext context) {
                var path = java.net.URI.create(arguments.path("url").asText()).getPath();
                executed.add(path.substring(1));
                return ToolResult.success("weather-" + path.substring(1));
            }
        };
    }

    private record Fixture(ReActLoop loop, AgentDefinition agent,
            Session session, List<String> executed) { }

    private record Catalog(OryxTool tool) implements ToolCatalog {
        @Override public Optional<OryxTool> find(String name) {
            return tool.getName().equals(name) ? Optional.of(tool) : Optional.empty();
        }
        @Override public List<OryxTool> availableTo(Set<String> names) {
            return names.contains(tool.getName()) ? List.of(tool) : List.of();
        }
    }

    private static class MemorySessionStore implements SessionStore, MemoryService {
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
            return sessions.values().stream()
                    .filter(session -> session.profileName().equals(profile)
                            && session.channel().equals(channel)
                            && session.userId().equals(user)
                            && session.status() == Session.Status.ACTIVE)
                    .findFirst();
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
        private final List<ToolInvocationRecord> tools = new ArrayList<>();
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
