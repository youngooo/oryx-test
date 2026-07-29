package org.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.oryxos.core.agent.ContextLoader;
import org.oryxos.core.memory.MemoryScope;
import org.oryxos.core.model.AgentDefinition;
import org.oryxos.core.model.LlmCallRecord;
import org.oryxos.core.model.Message;
import org.oryxos.core.model.Profile;
import org.oryxos.core.model.Session;
import org.oryxos.core.model.ToolInvocationRecord;
import org.oryxos.core.port.InvocationAuditStore;
import org.oryxos.core.port.ProviderGateway;
import org.oryxos.core.port.Sandbox;
import org.oryxos.core.port.SessionStore;
import org.oryxos.core.prompt.PromptBuilder;
import org.oryxos.core.react.ReActLoop;
import org.oryxos.core.service.AgentService;
import org.oryxos.core.session.SessionManager;
import org.oryxos.memory.DefaultMemoryService;
import org.oryxos.memory.LongTermMemoryStore;
import org.oryxos.memory.markdown.MarkdownMemoryStore;
import org.oryxos.memory.mem0.Mem0MemoryStore;
import org.oryxos.memory.sqlite.MemoryEntryEntity;
import org.oryxos.memory.sqlite.MemoryEntryRepository;
import org.oryxos.memory.sqlite.SqliteMemoryStore;
import org.oryxos.memory.tool.MemoryTools;
import org.oryxos.tool.registry.ToolRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

class MemoryAcceptanceTest {
    private static final String PREFERENCE =
            "我项目用 Spring Boot，部署在 K8s 上";
    @TempDir Path temp;

    @Test
    void defaultMarkdownAgentSavesPreferenceAndUsesItInANewConversation()
            throws Exception {
        var workspace = createWorkspace();
        var sessions = new InMemorySessionStore();
        var audit = new Audit();
        var markdownFile = workspace.resolve("memory/MEMORY.md");

        var firstRuntime = runtime(workspace, sessions,
                new MarkdownMemoryStore(markdownFile),
                new SavePreferenceProvider(), audit);
        assertThat(firstRuntime.registry().find("save_memory")).isPresent();
        assertThat(firstRuntime.registry().find("recall_memory")).isPresent();

        var first = firstRuntime.service().invoke(
                "preference", "cli", "demo-user", PREFERENCE);
        assertThat(first.response()).contains("已记住");
        assertThat(audit.tools).singleElement().satisfies(record -> {
            assertThat(record.toolName()).isEqualTo("save_memory");
            assertThat(record.success()).isTrue();
            assertThat(record.argumentsJson()).contains("Spring Boot", "K8s");
        });
        assertThat(Files.readString(markdownFile))
                .contains("Spring Boot", "K8s");

        new SessionManager(sessions).archive(
                first.session().sessionId(), Instant.now());
        var secondRuntime = runtime(workspace, sessions,
                new MarkdownMemoryStore(markdownFile),
                new RecommendDatabaseProvider(), audit);
        var second = secondRuntime.service().invoke(
                "preference", "cli", "demo-user",
                "帮我看看我的项目能用什么数据库");

        assertThat(second.session().sessionId())
                .isNotEqualTo(first.session().sessionId());
        assertThat(second.response())
                .contains("Spring Boot", "K8s", "PostgreSQL");
    }

    @Test
    void allBackendsAppendRecreateAndRecallByKeyword() throws Exception {
        var markdownFile = temp.resolve("contract/memory/MEMORY.md");
        assertRestartRecall(new MarkdownMemoryStore(markdownFile),
                () -> new MarkdownMemoryStore(markdownFile));

        verifySqliteRestartAndRecall();
        verifyMem0RestartAndRecall();
    }

    private Runtime runtime(Path workspace, SessionStore sessions,
            LongTermMemoryStore longTerm, ProviderGateway provider,
            Audit audit) {
        var mapper = new ObjectMapper();
        var memory = new DefaultMemoryService(
                new SessionManager(sessions), longTerm);
        var registry = new ToolRegistry(MemoryTools.create(memory, mapper));
        Sandbox sandbox = ignored -> { };
        var prompt = new PromptBuilder(new ContextLoader(workspace),
                memory, registry, Instant::now, ZoneId.of("Asia/Shanghai"));
        var executor = new org.oryxos.core.tool.ToolExecutor(
                registry, sandbox, audit, Instant::now, mapper, 0);
        var loop = new ReActLoop(prompt, provider, executor, memory,
                Instant::now, Duration.ofSeconds(10));
        var service = new AgentService(Map.of(
                "preference", preferenceAgent(workspace)), sessions,
                loop, Instant::now, workspace);
        return new Runtime(service, registry);
    }

    private Path createWorkspace() throws Exception {
        var workspace = temp.resolve("agent-workspace");
        var agentDirectory = workspace.resolve("agents/preference");
        Files.createDirectories(agentDirectory);
        Files.createDirectories(workspace.resolve("memory"));
        Files.writeString(agentDirectory.resolve("AGENT.md"),
                "# Preference Agent\nRemember durable project preferences.");
        Files.writeString(workspace.resolve("AGENTS.md"),
                "Use save_memory for durable project preferences.");
        return workspace;
    }

    private AgentDefinition preferenceAgent(Path workspace) {
        var agentFile = workspace.resolve("agents/preference/AGENT.md");
        var profile = new Profile("preference", "stub", "stub-model",
                Set.of("save_memory", "recall_memory"), Set.of("cli"),
                null, List.of(), 3, 0.0);
        return new AgentDefinition("preference", agentFile,
                "Remember durable project preferences and use them later.",
                profile, null, null, null,
                AgentDefinition.LoadStatus.VALID, List.of());
    }

    private static void assertRestartRecall(LongTermMemoryStore initial,
            Supplier<LongTermMemoryStore> restarted) {
        initial.append(PREFERENCE, MemoryScope.CORE);
        assertThat(restarted.get().recallByKeyword("Spring Boot"))
                .extracting(item -> item.content())
                .containsExactly(PREFERENCE);
    }

    private void verifySqliteRestartAndRecall() {
        var database = temp.resolve("memory-contract.db").toAbsolutePath();
        try (var first = sqliteContext(database)) {
            first.getBean(MemoryEntryRepository.class).deleteAll();
            new SqliteMemoryStore(first.getBean(MemoryEntryRepository.class))
                    .append(PREFERENCE, MemoryScope.CORE);
        }
        try (var restarted = sqliteContext(database)) {
            assertThat(new SqliteMemoryStore(
                    restarted.getBean(MemoryEntryRepository.class))
                    .recallByKeyword("K8s"))
                    .extracting(item -> item.content())
                    .containsExactly(PREFERENCE);
        }
    }

    private org.springframework.context.ConfigurableApplicationContext
            sqliteContext(Path database) {
        return new SpringApplication(SqliteTestConfiguration.class).run(
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=jdbc:sqlite:" + database,
                "--spring.datasource.driver-class-name=org.sqlite.JDBC",
                "--spring.jpa.hibernate.ddl-auto=none",
                "--spring.jpa.properties.hibernate.dialect="
                        + "org.hibernate.community.dialect.SQLiteDialect",
                "--spring.sql.init.mode=always",
                "--spring.sql.init.schema-locations=classpath:db/schema.sql",
                "--spring.autoconfigure.exclude="
                        + "org.springframework.ai.model.openai.autoconfigure."
                        + "OpenAiAudioSpeechAutoConfiguration,"
                        + "org.springframework.ai.model.openai.autoconfigure."
                        + "OpenAiAudioTranscriptionAutoConfiguration,"
                        + "org.springframework.ai.model.openai.autoconfigure."
                        + "OpenAiChatAutoConfiguration,"
                        + "org.springframework.ai.model.openai.autoconfigure."
                        + "OpenAiEmbeddingAutoConfiguration,"
                        + "org.springframework.ai.model.openai.autoconfigure."
                        + "OpenAiImageAutoConfiguration,"
                        + "org.springframework.ai.model.openai.autoconfigure."
                        + "OpenAiModerationAutoConfiguration");
    }

    private static void verifyMem0RestartAndRecall() throws Exception {
        var mapper = new ObjectMapper();
        var values = new ArrayList<com.fasterxml.jackson.databind.node.ObjectNode>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/memories/", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                var input = mapper.readTree(exchange.getRequestBody());
                var item = mapper.createObjectNode();
                item.put("id", "mem0-" + values.size());
                item.put("memory", input.path("memory").asText());
                item.set("metadata", input.path("metadata"));
                values.add(item);
                respond(exchange, "{}", 200);
            } else {
                respond(exchange, mapper.writeValueAsString(values), 200);
            }
        });
        server.createContext("/v1/memories/search/", exchange ->
                respond(exchange, mapper.writeValueAsString(values), 200));
        server.start();
        try {
            var endpoint = URI.create(
                    "http://localhost:" + server.getAddress().getPort());
            var first = new Mem0MemoryStore(endpoint, null, mapper);
            first.append(PREFERENCE, MemoryScope.CORE);
            var restarted = new Mem0MemoryStore(endpoint, null, mapper);
            assertThat(restarted.recallByKeyword("Spring Boot"))
                    .extracting(item -> item.content())
                    .containsExactly(PREFERENCE);
        } finally {
            server.stop(0);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange,
            String body, int status) throws java.io.IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static final class SavePreferenceProvider
            implements ProviderGateway {
        @Override
        public Response generate(Request request) {
            if (request.iteration() == 1) {
                assertThat(request.tools()).extracting(tool -> tool.name())
                        .contains("save_memory");
                return new Response("", List.of(new ToolCall(
                        "save-preference", "save_memory",
                        "{\"content\":\"" + PREFERENCE
                                + "\",\"scope\":\"CORE\"}")),
                        null, null, "tool_calls");
            }
            return new Response("已记住你的项目技术栈。", List.of(),
                    null, null, "stop");
        }
    }

    private static final class RecommendDatabaseProvider
            implements ProviderGateway {
        @Override
        public Response generate(Request request) {
            assertThat(request.messages()).extracting(message -> message.content())
                    .anySatisfy(content -> assertThat(content)
                            .contains("Spring Boot", "K8s"));
            return new Response(
                    "你的项目使用 Spring Boot 并部署在 K8s，建议优先采用 PostgreSQL。",
                    List.of(), null, null, "stop");
        }
    }

    private static final class InMemorySessionStore implements SessionStore {
        private final Map<String, Session> sessions = new LinkedHashMap<>();

        @Override
        public synchronized Session save(Session session) {
            sessions.put(session.sessionId(), session);
            return session;
        }

        @Override
        public synchronized Optional<Session> findById(String sessionId) {
            return Optional.ofNullable(sessions.get(sessionId));
        }

        @Override
        public synchronized Optional<Session> findActive(
                String profileName, String channel, String userId) {
            return sessions.values().stream().filter(session ->
                    session.status() == Session.Status.ACTIVE
                            && session.profileName().equals(profileName)
                            && session.channel().equals(channel)
                            && session.userId().equals(userId))
                    .findFirst();
        }

        @Override
        public synchronized Session append(String sessionId, Message message) {
            var updated = sessions.get(sessionId).append(message);
            sessions.put(sessionId, updated);
            return updated;
        }
    }

    private static final class Audit implements InvocationAuditStore {
        private final List<ToolInvocationRecord> tools = new ArrayList<>();

        @Override public void saveLlmCall(LlmCallRecord record) { }

        @Override
        public void saveToolInvocation(ToolInvocationRecord record) {
            tools.add(record);
        }

        @Override
        public Page<LlmCallRecord> findLlmCalls(
                String sessionId, int offset, int limit) {
            return new Page<>(List.of(), offset, limit, 0);
        }

        @Override
        public Page<ToolInvocationRecord> findToolInvocations(
                String sessionId, int offset, int limit) {
            var matching = tools.stream()
                    .filter(item -> item.sessionId().equals(sessionId)).toList();
            return new Page<>(matching, offset, limit, matching.size());
        }
    }

    private record Runtime(AgentService service, ToolRegistry registry) { }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = MemoryEntryEntity.class)
    @EnableJpaRepositories(basePackageClasses = MemoryEntryRepository.class)
    static class SqliteTestConfiguration { }
}
