package org.oryxos.core.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.oryxos.core.agent.ContextLoader;
import org.oryxos.core.memory.LongTermMemoryView;
import org.oryxos.core.memory.MemoryContext;
import org.oryxos.core.memory.MemoryScope;
import org.oryxos.core.model.AgentDefinition;
import org.oryxos.core.model.Message;
import org.oryxos.core.model.Profile;
import org.oryxos.core.model.Session;
import org.oryxos.core.model.ToolResult;
import org.oryxos.core.port.MemoryService;
import org.oryxos.core.port.ToolCatalog;
import org.oryxos.core.tool.OryxTool;
import org.oryxos.core.tool.ToolExecutionContext;

class PromptBuilderTest {

    @TempDir Path workspace;

    @Test
    void preservesContextOrderTimeToolSubsetAndLatestTwentyMessages() {
        var history = new ArrayList<Message>();
        for (int index = 1; index <= 25; index++) {
            history.add(message(index, Message.Role.USER, "history-" + index));
        }
        var memory = new StubMemory(new MemoryContext(history,
                List.of(new LongTermMemoryView("m1", MemoryScope.CORE,
                        "Shanghai", Instant.EPOCH)), ""));
        var catalog = new StubCatalog(Map.of(
                "http_get", tool("http_get"),
                "hidden", tool("hidden")));
        var builder = new PromptBuilder(new ContextLoader(workspace),
                memory, catalog, () -> Instant.parse("2026-07-29T01:00:00Z"),
                ZoneId.of("Asia/Shanghai"));

        var prompt = builder.build(agent(Set.of("http_get")), session(history));

        assertThat(prompt.messages().getFirst().content())
                .contains("Weather assistant", "2026-07-29T09:00+08:00");
        assertThat(prompt.messages()).extracting(Message::content)
                .contains("history-6", "history-25")
                .doesNotContain("history-5");
        assertThat(prompt.messages().get(prompt.messages().size() - 2).content())
                .contains("http_get").doesNotContain("hidden");
        assertThat(prompt.messages().getLast().content()).isEqualTo("history-25");
        assertThat(prompt.tools()).extracting(tool -> tool.name())
                .containsExactly("http_get");
    }

    @Test
    void rejectsCoreMemoryThatCannotFitMandatoryContext() {
        var memory = new StubMemory(new MemoryContext(List.of(),
                List.of(new LongTermMemoryView("m1", MemoryScope.CORE,
                        "x".repeat(1000), Instant.EPOCH)), ""));
        var builder = new PromptBuilder(new ContextLoader(workspace),
                memory, new StubCatalog(Map.of()), Instant::now,
                ZoneId.of("UTC"), 20, 100);

        assertThatThrownBy(() -> builder.build(agent(Set.of()), session(List.of())))
                .isInstanceOf(PromptBuilder.PromptTooLargeException.class)
                .hasMessageContaining("core memory");
    }

    private AgentDefinition agent(Set<String> tools) {
        var profile = new Profile("weather", "deepseek", "deepseek-chat",
                tools, Set.of("cli", "scheduler"), null, List.of(), 10, null);
        return new AgentDefinition("weather", workspace.resolve("AGENT.md"),
                "Weather assistant", profile, null, null, null,
                AgentDefinition.LoadStatus.VALID, List.of());
    }

    private Session session(List<Message> messages) {
        return new Session("session-1", "weather", "cli", "user",
                messages, Session.Status.ACTIVE, Instant.EPOCH,
                messages.isEmpty() ? Instant.EPOCH : messages.getLast().createdAt(),
                null, 0);
    }

    private Message message(long sequence, Message.Role role, String content) {
        return new Message("m-" + sequence, sequence, role, content,
                null, null, Instant.EPOCH.plusSeconds(sequence));
    }

    private OryxTool tool(String name) {
        var schema = new ObjectMapper().createObjectNode().put("type", "object");
        return new OryxTool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return "description-" + name; }
            @Override public com.fasterxml.jackson.databind.JsonNode getInputSchema() {
                return schema;
            }
            @Override public ToolResult execute(
                    com.fasterxml.jackson.databind.JsonNode arguments,
                    ToolExecutionContext context) {
                return ToolResult.success("ok");
            }
        };
    }

    private record StubMemory(MemoryContext context) implements MemoryService {
        @Override public MemoryContext loadContext(String id, Profile profile) {
            return context;
        }
        @Override public Session appendSessionMessage(String id, Message message) {
            throw new UnsupportedOperationException("Not used by PromptBuilder");
        }
        @Override public void remember(String content, MemoryScope scope) { }
    }

    private record StubCatalog(Map<String, OryxTool> tools) implements ToolCatalog {
        @Override public java.util.Optional<OryxTool> find(String name) {
            return java.util.Optional.ofNullable(tools.get(name));
        }
        @Override public List<OryxTool> availableTo(Set<String> names) {
            return names.stream().map(tools::get).toList();
        }
    }
}
