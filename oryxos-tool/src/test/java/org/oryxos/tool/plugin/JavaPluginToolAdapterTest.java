package org.oryxos.tool.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.oryxos.core.port.ClockProvider;
import org.oryxos.core.port.InvocationAuditStore;
import org.oryxos.core.tool.ToolExecutionContext;
import org.oryxos.core.tool.ToolExecutor;
import org.oryxos.tool.registry.ToolRegistry;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

class JavaPluginToolAdapterTest {

    @TempDir Path workspace;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void discoversSpringToolAndAdaptsSchemaArgumentsAndResult() throws Exception {
        var tools = JavaPluginToolAdapter.discover(mapper, List.of(new Plugin()));
        var tool = tools.getFirst();
        var parameterName = tool.getInputSchema().path("properties")
                .fieldNames().next();
        var result = tool.execute(mapper.createObjectNode().put(parameterName, "hello"),
                new ToolExecutionContext("session", "agent", workspace,
                        Instant.now().plusSeconds(5), "test", "user", Map.of()));

        assertThat(tool.getName()).isEqualTo("plugin_echo");
        assertThat(tool.getDescription()).isEqualTo("Echo from plugin");
        assertThat(result.success()).isTrue();
        assertThat(result.content()).contains("HELLO");
    }

    @Test
    void rejectsInvalidArgumentsNormalizesPluginFailureAndAuditsEveryAttempt() {
        var registry = new ToolRegistry(
                JavaPluginToolAdapter.discover(mapper, List.of(new Plugin())));
        var definition = registry.definitions().getFirst();
        assertThat(definition.origin()).isEqualTo(ToolDefinition.Origin.JAVA_PLUGIN);
        assertThat(definition.source()).contains(Plugin.class.getName());
        assertThat(definition.inputSchema().path("required"))
                .anyMatch(node -> node.asText().equals("text"));

        var audit = new Audit();
        var executor = new ToolExecutor(registry, action -> { }, audit,
                (ClockProvider) Instant::now, mapper, 0);
        var invalid = executor.execute("plugin_echo", "{}", context());
        var failed = executor.execute(
                "plugin_echo", "{\"text\":\"explode\"}", context());
        var success = executor.execute(
                "plugin_echo", "{\"text\":\"hello\"}", context());

        assertThat(invalid.success()).isFalse();
        assertThat(failed.success()).isFalse();
        assertThat(failed.error()).contains("IllegalStateException")
                .doesNotContain("plugin-secret");
        assertThat(success.content()).contains("HELLO");
        assertThat(audit.tools).hasSize(3);
        assertThat(audit.tools).extracting(ToolInvocationRecord::errorCode)
                .containsExactly("INVALID_ARGUMENT", "TOOL_ERROR", null);
    }

    private ToolExecutionContext context() {
        return new ToolExecutionContext("session", "agent", workspace,
                Instant.now().plusSeconds(5), "test", "user", Map.of());
    }

    static final class Plugin {
        @Tool(name = "plugin_echo", description = "Echo from plugin")
        String echo(@ToolParam(description = "text") String text) {
            if ("explode".equals(text)) {
                throw new IllegalStateException("plugin-secret");
            }
            return text.toUpperCase();
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
