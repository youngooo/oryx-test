package org.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.oryxos.core.agent.AgentLoader;
import org.oryxos.core.agent.ContextLoader;
import org.oryxos.core.memory.MemoryScope;
import org.oryxos.core.model.AgentDefinition;
import org.oryxos.core.model.LlmCallRecord;
import org.oryxos.core.model.Message;
import org.oryxos.core.model.Session;
import org.oryxos.core.model.ToolInvocationRecord;
import org.oryxos.core.model.ToolResult;
import org.oryxos.core.port.InvocationAuditStore;
import org.oryxos.core.port.ProviderGateway;
import org.oryxos.core.port.SessionStore;
import org.oryxos.core.prompt.PromptBuilder;
import org.oryxos.core.react.ReActLoop;
import org.oryxos.core.service.AgentService;
import org.oryxos.core.session.SessionManager;
import org.oryxos.memory.DefaultMemoryService;
import org.oryxos.memory.markdown.MarkdownMemoryStore;
import org.oryxos.tool.builtin.ReadFileTool;
import org.oryxos.tool.builtin.ShellTool;
import org.oryxos.tool.mcp.McpClientService;
import org.oryxos.tool.mcp.McpServerConfiguration;
import org.oryxos.tool.registry.ToolRegistry;
import org.oryxos.tool.sandbox.WhitelistSandbox;

class AgentResourceStageAcceptanceTest {

    private static final String MEMORY_MARKER =
            "TECH_MEMORY_MARKER_42: prioritize Spring Boot and Kubernetes relevance";
    private static final String SKILL_MARKER = "TECH_SKILL_MARKER_42";
    private static final String NEWS_MARKER = "NEWS_MCP_MARKER_42";
    private static final String SCRIPT_MARKER = "GITHUB_SCRIPT_MARKER_42";

    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void technologyDigestReadsSkillInvokesNewsMcpAndUsesSeededMemory()
            throws Exception {
        var runtime = runtime("technology-stage");

        var result = runtime.service().invoke(
                "tech-digest", "cli", "stage-user", "Generate today's digest");

        assertThat(result.response())
                .contains(SKILL_MARKER, NEWS_MARKER, MEMORY_MARKER);
        var invocations = runtime.audit().forSession(result.session().sessionId());
        assertThat(invocations).extracting(ToolInvocationRecord::toolName)
                .containsExactly("read_file", "news_search");
        assertThat(invocations).allSatisfy(record ->
                assertThat(record.success()).isTrue());
        assertThat(invocations.getFirst().argumentsJson())
                .contains("agents/tech-digest/skills/digest-format.md");
        assertThat(invocations.getFirst().resultSummary()).contains(SKILL_MARKER);
        assertThat(invocations.getLast().resultSummary()).contains(NEWS_MARKER);
    }

    @Test
    void githubDigestExecutesItsOwnScriptAsTheDeterministicSource()
            throws Exception {
        var runtime = runtime("github-stage");

        var result = runtime.service().invoke(
                "github-digest", "cli", "stage-user", "Generate today's digest");

        assertThat(result.response())
                .contains(SCRIPT_MARKER, "oryx-labs/oryxos");
        var invocations = runtime.audit().forSession(result.session().sessionId());
        assertThat(invocations).singleElement().satisfies(record -> {
            assertThat(record.toolName()).isEqualTo("shell");
            assertThat(record.success()).isTrue();
            assertThat(record.argumentsJson())
                    .contains("scripts/github-digest.ps1")
                    .contains("agents/github-digest");
            assertThat(record.resultSummary())
                    .contains(SCRIPT_MARKER, "oryx-labs/oryxos");
        });
    }

    private Runtime runtime(String directory) throws Exception {
        var workspace = temp.resolve(directory).resolve(".oryxos");
        copyFixture(workspace, "tech-digest", "AGENT.md");
        copyFixture(workspace, "tech-digest", "skills/digest-format.md");
        copyFixture(workspace, "github-digest", "AGENT.md");
        copyFixture(workspace, "github-digest", "scripts/github-digest.ps1");
        Files.writeString(workspace.resolve("AGENTS.md"),
                "Use only the Tools declared by the current Agent.");

        var sessions = new InMemorySessionStore();
        var memory = new DefaultMemoryService(new SessionManager(sessions),
                new MarkdownMemoryStore(workspace.resolve("memory/MEMORY.md")));
        memory.remember(MEMORY_MARKER, MemoryScope.CORE);

        var newsSchema = mapper.createObjectNode();
        newsSchema.put("type", "object");
        newsSchema.put("additionalProperties", false);
        newsSchema.putObject("properties").putObject("query").put("type", "string");
        newsSchema.putArray("required").add("query");
        var newsConfig = new McpServerConfiguration("news-fixture",
                McpServerConfiguration.Transport.HTTP, null, List.of(),
                URI.create("http://localhost/news-mcp"), Map.of());
        var newsMcp = new McpClientService(List.of(newsConfig), ignored ->
                new McpClientService.Client() {
                    @Override
                    public List<McpClientService.RemoteTool> listTools() {
                        return List.of(new McpClientService.RemoteTool(
                                "news_search", "Search deterministic fixture news",
                                newsSchema));
                    }

                    @Override
                    public ToolResult call(String name, JsonNode arguments) {
                        if (!"news_search".equals(name)
                                || arguments.path("query").asText().isBlank()) {
                            return ToolResult.failure("Invalid fixture query", false);
                        }
                        return ToolResult.success(
                                NEWS_MARKER + ": Java platform update");
                    }
                });

        var registry = new ToolRegistry(List.of(
                new ReadFileTool(mapper),
                new ShellTool(mapper),
                newsMcp.tools().getFirst()));
        var shellCommand = isWindows() ? "powershell.exe" : "pwsh";
        var sandbox = new WhitelistSandbox(Set.of(),
                Set.of(shellCommand), Set.of(workspace));
        var agents = new AgentLoader(Set.of("stub"),
                registry.all().stream().map(tool -> tool.getName())
                        .collect(Collectors.toSet()),
                Set.of("cli"), ZoneId.of("Asia/Shanghai"))
                .load(workspace).stream()
                .collect(Collectors.toMap(
                        AgentDefinition::name, agent -> agent));
        assertThat(agents).containsOnlyKeys("tech-digest", "github-digest");
        assertThat(agents.values()).allSatisfy(agent -> {
            assertThat(agent.loadStatus()).isEqualTo(
                    AgentDefinition.LoadStatus.VALID);
            assertThat(agent.validationErrors()).isEmpty();
        });

        var audit = new Audit();
        var prompt = new PromptBuilder(new ContextLoader(workspace),
                memory, registry, Instant::now, ZoneId.of("Asia/Shanghai"));
        var executor = new org.oryxos.core.tool.ToolExecutor(
                registry, sandbox, audit, Instant::now, mapper, 0);
        var provider = new StageProvider(mapper, shellCommand, isWindows());
        var loop = new ReActLoop(prompt, provider, executor, memory,
                Instant::now, Duration.ofSeconds(30));
        var service = new AgentService(agents, sessions, loop,
                Instant::now, workspace);
        return new Runtime(service, audit);
    }

    private void copyFixture(Path workspace, String agent, String relative)
            throws IOException {
        var resource = "/demos/" + agent + "/" + relative;
        var destination = workspace.resolve("agents")
                .resolve(agent).resolve(relative);
        Files.createDirectories(destination.getParent());
        try (var source = getClass().getResourceAsStream(resource)) {
            assertThat(source).as(resource).isNotNull();
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    private static final class StageProvider implements ProviderGateway {
        private final ObjectMapper mapper;
        private final String shellCommand;
        private final boolean windows;

        private StageProvider(ObjectMapper mapper, String shellCommand,
                boolean windows) {
            this.mapper = mapper;
            this.shellCommand = shellCommand;
            this.windows = windows;
        }

        @Override
        public Response generate(Request request) {
            var toolNames = request.tools().stream()
                    .map(tool -> tool.name()).collect(Collectors.toSet());
            if (toolNames.contains("news_search")) {
                return technology(request);
            }
            if (toolNames.contains("shell")) {
                return github(request);
            }
            throw new IllegalStateException("Unexpected stage Agent");
        }

        private Response technology(Request request) {
            var prompt = prompt(request);
            return switch (request.iteration()) {
                case 1 -> {
                    assertThat(prompt)
                            .contains("Daily Technology Digest", MEMORY_MARKER);
                    yield tool("read-skill", "read_file", Map.of(
                            "path",
                            "agents/tech-digest/skills/digest-format.md"));
                }
                case 2 -> {
                    assertThat(prompt).contains(SKILL_MARKER, MEMORY_MARKER);
                    yield tool("search-news", "news_search",
                            Map.of("query", "enterprise Java technology"));
                }
                case 3 -> {
                    assertThat(prompt)
                            .contains(SKILL_MARKER, NEWS_MARKER, MEMORY_MARKER);
                    yield finalResponse("Technology digest\n"
                            + SKILL_MARKER + "\n"
                            + NEWS_MARKER + "\n"
                            + MEMORY_MARKER);
                }
                default -> throw new IllegalStateException(
                        "Unexpected technology iteration");
            };
        }

        private Response github(Request request) {
            var prompt = prompt(request);
            if (request.iteration() == 1) {
                assertThat(prompt)
                        .contains("Daily GitHub Digest",
                                "scripts/github-digest.ps1");
                var arguments = new LinkedHashMap<String, Object>();
                arguments.put("command", shellCommand);
                var args = new ArrayList<String>();
                args.add("-NoProfile");
                if (windows) {
                    args.add("-ExecutionPolicy");
                    args.add("Bypass");
                }
                args.add("-File");
                args.add("scripts/github-digest.ps1");
                arguments.put("args", args);
                arguments.put("workingDirectory", "agents/github-digest");
                arguments.put("timeoutSeconds", 10);
                return tool("run-github-script", "shell", arguments);
            }
            assertThat(request.iteration()).isEqualTo(2);
            assertThat(prompt).contains(SCRIPT_MARKER, "oryx-labs/oryxos");
            return finalResponse("GitHub digest\noryx-labs/oryxos\n"
                    + SCRIPT_MARKER);
        }

        private Response tool(String id, String name,
                Map<String, ?> arguments) {
            try {
                return new Response("", List.of(new ToolCall(
                        id, name, mapper.writeValueAsString(arguments))),
                        null, null, "tool_calls");
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
        }

        private Response finalResponse(String content) {
            return new Response(content, List.of(),
                    null, null, "stop");
        }

        private String prompt(Request request) {
            return request.messages().stream()
                    .map(Message::content).collect(Collectors.joining("\n"));
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
        public synchronized void saveToolInvocation(
                ToolInvocationRecord record) {
            tools.add(record);
        }

        private synchronized List<ToolInvocationRecord> forSession(
                String sessionId) {
            return tools.stream().filter(record ->
                    record.sessionId().equals(sessionId)).toList();
        }

        @Override
        public Page<LlmCallRecord> findLlmCalls(
                String sessionId, int offset, int limit) {
            return new Page<>(List.of(), offset, limit, 0);
        }

        @Override
        public Page<ToolInvocationRecord> findToolInvocations(
                String sessionId, int offset, int limit) {
            var matching = forSession(sessionId);
            return new Page<>(matching, offset, limit, matching.size());
        }
    }

    private record Runtime(AgentService service, Audit audit) { }
}
