package org.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.oryxos.core.model.ToolDefinition;
import org.oryxos.provider.adapter.FunctionCallingAdapter;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

class FunctionCallingAdapterTest {

    @Test
    void createsSchemaOnlyOptionsWithInternalExecutionDisabled() throws Exception {
        var schema = new ObjectMapper().readTree("""
                {"type":"object","properties":{"url":{"type":"string"}}}
                """);
        var options = new FunctionCallingAdapter().options("deepseek-chat", 0.2,
                List.of(new ToolDefinition("http_get", "Fetch a URL", schema,
                        ToolDefinition.Origin.BUILT_IN, "oryxos-tool")));

        assertThat(options).isInstanceOf(ToolCallingChatOptions.class);
        var toolOptions = (ToolCallingChatOptions) options;
        assertThat(toolOptions.getInternalToolExecutionEnabled()).isFalse();
        assertThat(toolOptions.getToolCallbacks()).singleElement().satisfies(callback -> {
            assertThat(callback.getToolDefinition().name()).isEqualTo("http_get");
            assertThat(callback.getToolDefinition().inputSchema()).contains("\"url\"");
            assertThatThrownBy(() -> callback.call("{\"url\":\"https://example.com\"}"))
                    .isInstanceOf(UnsupportedOperationException.class);
        });
    }

    @Test
    void containsNoAdvisorOrToolCallingManagerDispatcher() {
        assertThat(List.of(FunctionCallingAdapter.class.getDeclaredFields()))
                .noneMatch(field -> field.getType().getName().contains("Advisor")
                        || field.getType().getName().contains("ToolCallingManager"));
    }
}
