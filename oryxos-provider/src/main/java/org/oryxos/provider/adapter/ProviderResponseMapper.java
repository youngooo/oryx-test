package org.oryxos.provider.adapter;

import org.oryxos.core.port.ProviderGateway;
import org.springframework.ai.chat.model.ChatResponse;

public final class ProviderResponseMapper {

    public ProviderGateway.Response from(ChatResponse response) {
        if (response == null || response.getResult() == null
                || response.getResult().getOutput() == null) {
            throw new IllegalStateException("Provider returned an empty response");
        }
        var generation = response.getResult();
        var output = generation.getOutput();
        var metadata = response.getMetadata();
        var usage = metadata == null ? null : metadata.getUsage();
        var finishReason = generation.getMetadata() == null
                ? null : generation.getMetadata().getFinishReason();
        var toolCalls = output.getToolCalls().stream()
                .map(call -> new ProviderGateway.ToolCall(
                        call.id(), call.name(), call.arguments()))
                .toList();
        return new ProviderGateway.Response(output.getText(), toolCalls,
                usage == null || usage.getPromptTokens() == null
                        ? null : usage.getPromptTokens().longValue(),
                usage == null || usage.getCompletionTokens() == null
                        ? null : usage.getCompletionTokens().longValue(),
                finishReason);
    }
}
