package org.oryxos.provider.adapter;

import java.util.List;
import org.oryxos.core.model.ToolDefinition;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

/**
 * Converts OryxOS Tool schemas into Spring AI options without delegating execution.
 */
public final class FunctionCallingAdapter {

    public ChatOptions options(String model, Double temperature,
            List<ToolDefinition> tools) {
        var callbacks = tools == null ? List.<ToolCallback>of()
                : tools.stream().map(this::schemaOnlyCallback).toList();
        return DefaultToolCallingChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .toolCallbacks(callbacks)
                .internalToolExecutionEnabled(false)
                .build();
    }

    private ToolCallback schemaOnlyCallback(ToolDefinition definition) {
        var springDefinition = org.springframework.ai.tool.definition.ToolDefinition
                .builder()
                .name(definition.name())
                .description(definition.description())
                .inputSchema(definition.inputSchema().toString())
                .build();
        return new ToolCallback() {
            @Override
            public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return springDefinition;
            }

            @Override
            public String call(String toolInput) {
                throw new UnsupportedOperationException(
                        "OryxOS ReActLoop is the sole Tool dispatcher");
            }
        };
    }
}
