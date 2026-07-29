package org.oryxos.provider.adapter;

import java.util.List;
import org.oryxos.core.model.Message;
import org.oryxos.core.port.ProviderGateway;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

public final class ProviderRequestMapper {

    private final FunctionCallingAdapter functionCallingAdapter;

    public ProviderRequestMapper(FunctionCallingAdapter functionCallingAdapter) {
        this.functionCallingAdapter = functionCallingAdapter;
    }

    public Prompt toPrompt(ProviderGateway.Request request, String effectiveModel) {
        var messages = request.messages().stream().map(this::toMessage).toList();
        var options = functionCallingAdapter.options(effectiveModel,
                request.temperature(), request.tools());
        return new Prompt(messages, options);
    }

    private org.springframework.ai.chat.messages.Message toMessage(Message message) {
        return switch (message.role()) {
            case SYSTEM -> new SystemMessage(message.content());
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> AssistantMessage.builder()
                    .content(message.content())
                    .toolCalls(message.toolRequests().stream()
                            .map(call -> new AssistantMessage.ToolCall(
                                    call.id(), "function", call.name(),
                                    call.argumentsJson()))
                            .toList())
                    .build();
            case TOOL -> ToolResponseMessage.builder()
                    .responses(List.of(new ToolResponseMessage.ToolResponse(
                            message.toolCallId(), message.toolName(), message.content())))
                    .build();
        };
    }
}
