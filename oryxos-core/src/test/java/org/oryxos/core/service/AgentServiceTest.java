package org.oryxos.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.oryxos.core.tool.ToolExecutor;

class AgentServiceTest {

    @TempDir Path workspace;

    @Test
    void allChannelsShareOneEntryPathAndCoordinatorContextIsCleaned() {
        var store = new Store();
        var profile = new Profile("weather", "deepseek", "deepseek-chat",
                Set.of(), Set.of("cli", "api", "scheduler"), null,
                List.of(), 10, null);
        var agent = new AgentDefinition("weather",
                workspace.resolve("AGENT.md"), "Weather assistant", profile,
                null, null, null, AgentDefinition.LoadStatus.VALID, List.of());
        ToolCatalog emptyTools = new ToolCatalog() {
            @Override public Optional<org.oryxos.core.tool.OryxTool> find(String name) {
                return Optional.empty();
            }
            @Override public List<org.oryxos.core.tool.OryxTool> availableTo(
                    Set<String> names) {
                return List.of();
            }
        };
        var prompt = new PromptBuilder(new ContextLoader(workspace),
                store, emptyTools, Instant::now, ZoneId.of("UTC"));
        ProviderGateway provider = request -> new ProviderGateway.Response(
                request.messages().getLast().content() + "-ok",
                List.of(), null, null, "stop");
        var audit = new Audit();
        var executor = new ToolExecutor(emptyTools, action -> { }, audit,
                Instant::now, new ObjectMapper(), 0);
        var loop = new ReActLoop(prompt, provider, executor, store,
                Instant::now, Duration.ofSeconds(5));
        var service = new AgentService(Map.of("weather", agent), store,
                loop, Instant::now, workspace);

        assertThat(service.invoke("weather", "cli", "u1", "cli").response())
                .isEqualTo("cli-ok");
        assertThat(service.invoke("weather", "api", "u2", "api").response())
                .isEqualTo("api-ok");
        assertThat(service.invoke("weather", "scheduler", "u3", "timer").response())
                .isEqualTo("timer-ok");
        assertThat(service.activeCoordinatorCount()).isZero();
        assertThat(store.sessions.values()).extracting(Session::channel)
                .containsExactlyInAnyOrder("cli", "api", "scheduler");
    }

    @Test
    void concurrentTurnsForOneSessionNeverOverlapAndCleanTheirCoordinator()
            throws Exception {
        var store = new Store();
        var profile = new Profile("weather", "deepseek", "deepseek-chat",
                Set.of(), Set.of("cli"), null, List.of(), 10, null);
        var agent = new AgentDefinition("weather",
                workspace.resolve("AGENT.md"), "Weather assistant", profile,
                null, null, null, AgentDefinition.LoadStatus.VALID, List.of());
        var now = Instant.now();
        store.save(new Session("shared", "weather", "cli", "user",
                List.of(), Session.Status.ACTIVE, now, now, null, 0));
        var active = new AtomicInteger();
        var maximumActive = new AtomicInteger();
        ProviderGateway provider = request -> {
            var current = active.incrementAndGet();
            maximumActive.accumulateAndGet(current, Math::max);
            try {
                Thread.sleep(40);
                return new ProviderGateway.Response("ok", List.of(),
                        null, null, "stop");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", interrupted);
            } finally {
                active.decrementAndGet();
            }
        };
        ToolCatalog emptyTools = new ToolCatalog() {
            @Override public Optional<org.oryxos.core.tool.OryxTool> find(String name) {
                return Optional.empty();
            }
            @Override public List<org.oryxos.core.tool.OryxTool> availableTo(
                    Set<String> names) {
                return List.of();
            }
        };
        var prompt = new PromptBuilder(new ContextLoader(workspace),
                store, emptyTools, Instant::now, ZoneId.of("UTC"));
        var loop = new ReActLoop(prompt, provider,
                new ToolExecutor(emptyTools, action -> { }, new Audit(),
                        Instant::now, new ObjectMapper(), 0),
                store, Instant::now, Duration.ofSeconds(5));
        var service = new AgentService(Map.of("weather", agent), store,
                loop, Instant::now, workspace);

        try (var callers = Executors.newFixedThreadPool(2)) {
            var first = callers.submit(() ->
                    service.invokeSession("weather", "shared", "first"));
            var second = callers.submit(() ->
                    service.invokeSession("weather", "shared", "second"));
            assertThat(first.get(2, TimeUnit.SECONDS).response()).isEqualTo("ok");
            assertThat(second.get(2, TimeUnit.SECONDS).response()).isEqualTo("ok");
        }

        assertThat(maximumActive).hasValue(1);
        assertThat(store.sessions.get("shared").messages()).hasSize(4);
        assertThat(service.activeCoordinatorCount()).isZero();
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
            return sessions.values().stream()
                    .filter(value -> value.profileName().equals(profile)
                            && value.channel().equals(channel)
                            && value.userId().equals(user)
                            && value.status() == Session.Status.ACTIVE)
                    .findFirst();
        }
        @Override public synchronized Session append(String id, Message message) {
            var value = sessions.get(id).append(message);
            sessions.put(id, value);
            return value;
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
        @Override public void saveLlmCall(LlmCallRecord record) { }
        @Override public void saveToolInvocation(ToolInvocationRecord record) { }
        @Override public Page<LlmCallRecord> findLlmCalls(
                String id, int offset, int limit) {
            return new Page<>(List.of(), offset, limit, 0);
        }
        @Override public Page<ToolInvocationRecord> findToolInvocations(
                String id, int offset, int limit) {
            return new Page<>(List.of(), offset, limit, 0);
        }
    }
}
