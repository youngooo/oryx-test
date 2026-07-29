package org.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.oryxos.core.agent.AgentLoader;
import org.oryxos.core.agent.ContextLoader;
import org.oryxos.core.memory.MemoryContext;
import org.oryxos.core.memory.MemoryScope;
import org.oryxos.core.model.LlmCallRecord;
import org.oryxos.core.model.Message;
import org.oryxos.core.model.Profile;
import org.oryxos.core.model.Session;
import org.oryxos.core.model.ToolInvocationRecord;
import org.oryxos.core.port.InvocationAuditStore;
import org.oryxos.core.port.MemoryService;
import org.oryxos.core.port.ProviderGateway;
import org.oryxos.core.port.SessionStore;
import org.oryxos.core.prompt.PromptBuilder;
import org.oryxos.core.react.ReActLoop;
import org.oryxos.core.service.AgentService;
import org.oryxos.channel.cli.CliChannel;
import org.oryxos.provider.config.OpenAiCompatibleChatModelFactory;
import org.oryxos.provider.config.ProviderConfiguration;
import org.oryxos.provider.config.ProviderConfiguration.ProviderSpec;
import org.oryxos.provider.service.ProviderService;
import org.oryxos.tool.builtin.HttpGetTool;
import org.oryxos.tool.registry.ToolRegistry;
import org.oryxos.tool.sandbox.WhitelistSandbox;

class WeatherDemoAcceptanceTest {

    private static final String WEATHER_HOST = "api.open-meteo.com";
    @TempDir Path temp;

    @Test
    void manualCliWeatherChatUsesReactHttpAndOneAccumulatedSession()
            throws Exception {
        var apiKey = System.getenv("DEEPSEEK_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                "Set DEEPSEEK_API_KEY to run the live LLM weather Demo");
        var workspace = temp.resolve(".oryxos");
        var agentDirectory = workspace.resolve("agents/weather");
        Files.createDirectories(agentDirectory);
        try (var source = getClass().getResourceAsStream("/demos/weather/AGENT.md")) {
            assertThat(source).isNotNull();
            Files.copy(source, agentDirectory.resolve("AGENT.md"));
        }
        Files.writeString(workspace.resolve("AGENTS.md"),
                "Use only approved tools.");
        Files.writeString(workspace.resolve("SOUL.md"), "Be practical.");
        Files.writeString(workspace.resolve("USER.md"), "Location: Shanghai.");

        var mapper = new ObjectMapper();
        var sandbox = new WhitelistSandbox(Set.of(WEATHER_HOST),
                Set.of(), Set.of(workspace));
        var registry = new ToolRegistry(List.of(new HttpGetTool(
                HttpGetTool.safeClient(), sandbox, mapper)));
        var definitions = new AgentLoader(Set.of("deepseek"),
                Set.of("http_get"), Set.of("cli"),
                ZoneId.of("Asia/Shanghai")).load(workspace);
        assertThat(definitions).singleElement().satisfies(agent ->
                assertThat(agent.validationErrors()).isEmpty());
        var agent = definitions.getFirst();

        var store = new Store();
        var audit = new Audit();
        var spec = new ProviderSpec("deepseek",
                environment("DEEPSEEK_MODEL", "deepseek-v4-flash"), apiKey,
                environment("DEEPSEEK_BASE_URL", "https://api.deepseek.com"));
        var chatModel = OpenAiCompatibleChatModelFactory.create(spec);
        ProviderGateway provider = new ProviderService(
                ProviderConfiguration.bind(List.of(spec),
                        Map.of("deepseek", chatModel)),
                audit, Instant::now);
        var prompt = new PromptBuilder(new ContextLoader(workspace),
                store, registry, Instant::now, ZoneId.of("Asia/Shanghai"));
        var executor = new org.oryxos.core.tool.ToolExecutor(
                registry, sandbox, audit, Instant::now, mapper, 0);
        var loop = new ReActLoop(prompt, provider, executor, store,
                Instant::now, Duration.ofSeconds(90));
        var service = new AgentService(Map.of("weather", agent), store,
                loop, Instant::now, workspace);
        var channel = new CliChannel(service);
        var output = new StringWriter();
        var exitCode = channel.chat("weather", "demo-user",
                new StringReader("""
                        查询今天上海的实时天气并给出穿搭建议。
                        再查一次实时天气，结合上一轮会话给出更精简的穿搭建议。
                        /exit
                        """),
                output);

        assertThat(exitCode).isZero();
        assertThat(output.toString()).contains("穿搭");
        assertThat(audit.llmCalls).hasSizeGreaterThanOrEqualTo(4)
                .allMatch(LlmCallRecord::success);
        assertThat(audit.llmCalls).extracting(LlmCallRecord::provider)
                .containsOnly("deepseek");
        assertThat(audit.tools).hasSizeGreaterThanOrEqualTo(2)
                .allMatch(ToolInvocationRecord::success);
        assertThat(audit.tools).extracting(ToolInvocationRecord::argumentsJson)
                .allSatisfy(arguments -> assertThat(arguments)
                        .contains(WEATHER_HOST));
        assertThat(store.sessions.values()).extracting(Session::channel)
                .containsExactly("cli");
        assertThat(store.sessions.values()).allSatisfy(session ->
                assertThat(session.messages()).satisfies(messages -> {
                    assertThat(messages).filteredOn(message ->
                            message.role() == Message.Role.USER).hasSize(2);
                    assertThat(messages).extracting(Message::role)
                        .containsSubsequence(
                                Message.Role.USER,
                                Message.Role.ASSISTANT,
                                Message.Role.TOOL,
                                Message.Role.ASSISTANT,
                                Message.Role.USER);
                }));

        System.out.println("DEMO_CLI=" + output.toString().trim());
    }

    private String environment(String name, String fallback) {
        var value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
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
        private final List<LlmCallRecord> llmCalls = new ArrayList<>();
        private final List<ToolInvocationRecord> tools = new ArrayList<>();
        @Override public void saveLlmCall(LlmCallRecord record) {
            llmCalls.add(record);
        }
        @Override public void saveToolInvocation(ToolInvocationRecord record) {
            tools.add(record);
        }
        @Override public Page<LlmCallRecord> findLlmCalls(
                String id, int offset, int limit) {
            return new Page<>(llmCalls, offset, limit, llmCalls.size());
        }
        @Override public Page<ToolInvocationRecord> findToolInvocations(
                String id, int offset, int limit) {
            return new Page<>(tools, offset, limit, tools.size());
        }
    }
}
