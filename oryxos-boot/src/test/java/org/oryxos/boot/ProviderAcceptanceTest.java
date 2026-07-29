package org.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
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
import org.oryxos.core.agent.ContextLoader;
import org.oryxos.core.memory.MemoryContext;
import org.oryxos.core.memory.MemoryScope;
import org.oryxos.core.model.AgentDefinition;
import org.oryxos.core.model.LlmCallRecord;
import org.oryxos.core.model.Message;
import org.oryxos.core.model.Profile;
import org.oryxos.core.model.Session;
import org.oryxos.core.model.ToolInvocationRecord;
import org.oryxos.core.port.InvocationAuditStore;
import org.oryxos.core.port.MemoryService;
import org.oryxos.core.port.ProviderGateway;
import org.oryxos.core.port.SessionStore;
import org.oryxos.core.port.ToolCatalog;
import org.oryxos.core.prompt.PromptBuilder;
import org.oryxos.core.react.ReActLoop;
import org.oryxos.core.service.AgentService;
import org.oryxos.core.tool.OryxTool;
import org.oryxos.core.tool.ToolExecutor;
import org.oryxos.provider.config.OpenAiCompatibleChatModelFactory;
import org.oryxos.provider.config.ProviderConfiguration;
import org.oryxos.provider.config.ProviderConfiguration.ProviderBinding;
import org.oryxos.provider.config.ProviderConfiguration.ProviderSpec;
import org.oryxos.provider.service.ProviderService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

class ProviderAcceptanceTest {

    @TempDir
    Path workspace;

    @Test
    void invokesBothNamedStubModelsWithoutRealCredentials() {
        var audit = new Audit();
        var service = new ProviderService(List.of(
                new ProviderBinding("deepseek", "deepseek-v4-flash",
                        prompt -> response("deepseek-ok"), List.of()),
                new ProviderBinding("kimi", "kimi-k2.6",
                        prompt -> response("kimi-ok"), List.of())),
                audit, Instant::now);

        var deepseek = service.generate(request("session-d", "deepseek"));
        var kimi = service.generate(request("session-k", "kimi"));

        assertThat(deepseek.content()).isEqualTo("deepseek-ok");
        assertThat(kimi.content()).isEqualTo("kimi-ok");
        assertThat(audit.calls).extracting(LlmCallRecord::provider)
                .containsExactly("deepseek", "kimi");
        assertThat(audit.calls).allMatch(LlmCallRecord::success);
    }

    @Test
    void invokesLiveDeepSeekWhenCredentialIsAvailable() {
        liveProviderFromEnvironment("deepseek", "DEEPSEEK_API_KEY",
                "DEEPSEEK_MODEL", "deepseek-v4-flash",
                "DEEPSEEK_BASE_URL", "https://api.deepseek.com");
    }

    @Test
    void invokesLiveKimiWhenCredentialIsAvailable() {
        var key = environment("KIMI_API_KEY", null);
        if (key == null) {
            key = environment("MOONSHOT_API_KEY", null);
        }
        Assumptions.assumeTrue(key != null,
                "Set KIMI_API_KEY or MOONSHOT_API_KEY to run the live Kimi smoke");
        liveProvider("kimi", key, "KIMI_MODEL", "kimi-k2.6",
                "KIMI_BASE_URL", "https://api.moonshot.cn");
    }

    private void liveProviderFromEnvironment(String name, String keyVariable,
            String modelVariable, String defaultModel,
            String baseUrlVariable, String defaultBaseUrl) {
        var key = environment(keyVariable, null);
        Assumptions.assumeTrue(key != null,
                "Set " + keyVariable + " to run the live " + name + " smoke");
        liveProvider(name, key, modelVariable, defaultModel,
                baseUrlVariable, defaultBaseUrl);
    }

    private void liveProvider(String name, String key,
            String modelVariable, String defaultModel,
            String baseUrlVariable, String defaultBaseUrl) {
        var spec = new ProviderSpec(name,
                environment(modelVariable, defaultModel), key,
                environment(baseUrlVariable, defaultBaseUrl));
        var model = OpenAiCompatibleChatModelFactory.create(spec);
        var audit = new Audit();
        var provider = new ProviderService(
                ProviderConfiguration.bind(List.of(spec), Map.of(name, model)),
                audit, Instant::now);
        var store = new Store();
        var tools = new EmptyTools();
        var prompt = new PromptBuilder(new ContextLoader(workspace),
                store, tools, Instant::now, ZoneId.of("UTC"));
        var executor = new ToolExecutor(tools, action -> { }, audit,
                Instant::now, new ObjectMapper(), 0);
        var loop = new ReActLoop(prompt, provider, executor, store,
                Instant::now, Duration.ofSeconds(60));
        var profile = new Profile("provider-smoke", name, spec.model(),
                Set.of(), Set.of("test"), null, List.of(), 1, 0.0);
        var agent = new AgentDefinition("provider-smoke",
                workspace.resolve("AGENT.md"),
                "Reply briefly and do not request tools.", profile,
                null, null, null, AgentDefinition.LoadStatus.VALID, List.of());
        var service = new AgentService(Map.of(agent.name(), agent), store,
                loop, Instant::now, workspace);

        var result = service.invoke(agent.name(), "test", "acceptance-user",
                "Reply with a short confirmation that the connection works.");

        assertThat(result.response()).isNotBlank();
        assertThat(result.terminationReason())
                .isEqualTo(ReActLoop.TerminationReason.FINAL_RESPONSE);
        assertThat(result.session().messages()).extracting(Message::role)
                .containsExactly(Message.Role.USER, Message.Role.ASSISTANT);
        assertThat(audit.calls).singleElement().satisfies(call -> {
            assertThat(call.success()).isTrue();
            assertThat(call.provider()).isEqualTo(name);
            assertThat(call.model()).isEqualTo(spec.model());
        });
    }

    private String environment(String name, String fallback) {
        var value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private ProviderGateway.Request request(String session, String provider) {
        return new ProviderGateway.Request(session, provider, null, 1,
                List.of(), List.of(), null);
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(
                new Generation(new AssistantMessage(text))));
    }

    private static class EmptyTools implements ToolCatalog {
        @Override
        public Optional<OryxTool> find(String name) {
            return Optional.empty();
        }

        @Override
        public List<OryxTool> availableTo(Set<String> names) {
            return List.of();
        }
    }

    private static class Store implements SessionStore, MemoryService {
        private final Map<String, Session> sessions = new LinkedHashMap<>();

        @Override
        public Session save(Session session) {
            sessions.put(session.sessionId(), session);
            return session;
        }

        @Override
        public Optional<Session> findById(String sessionId) {
            return Optional.ofNullable(sessions.get(sessionId));
        }

        @Override
        public Optional<Session> findActive(
                String profileName, String channel, String userId) {
            return sessions.values().stream()
                    .filter(session -> session.profileName().equals(profileName)
                            && session.channel().equals(channel)
                            && session.userId().equals(userId)
                            && session.status() == Session.Status.ACTIVE)
                    .findFirst();
        }

        @Override
        public Session append(String sessionId, Message message) {
            var session = sessions.get(sessionId).append(message);
            sessions.put(sessionId, session);
            return session;
        }

        @Override
        public MemoryContext loadContext(String sessionId, Profile profile) {
            return new MemoryContext(
                    sessions.get(sessionId).messages(), List.of(), "");
        }

        @Override
        public Session appendSessionMessage(
                String sessionId, Message message) {
            return append(sessionId, message);
        }

        @Override
        public void remember(String content, MemoryScope scope) {
        }
    }

    private static class Audit implements InvocationAuditStore {
        final List<LlmCallRecord> calls = new ArrayList<>();
        @Override public void saveLlmCall(LlmCallRecord record) { calls.add(record); }
        @Override public void saveToolInvocation(ToolInvocationRecord record) { }
        @Override public Page<LlmCallRecord> findLlmCalls(String id, int offset, int limit) {
            return new Page<>(calls, offset, limit, calls.size());
        }
        @Override public Page<ToolInvocationRecord> findToolInvocations(
                String id, int offset, int limit) {
            return new Page<>(List.of(), offset, limit, 0);
        }
    }
}
