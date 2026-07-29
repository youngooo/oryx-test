package org.oryxos.provider.config;

import org.oryxos.provider.config.ProviderConfiguration.ProviderSpec;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * Creates real Spring AI clients for OpenAI-compatible providers.
 */
public final class OpenAiCompatibleChatModelFactory {

    private OpenAiCompatibleChatModelFactory() {
    }

    public static ChatModel create(ProviderSpec spec) {
        ProviderConfiguration.validateSpec(spec);
        var api = OpenAiApi.builder()
                .baseUrl(spec.baseUrl())
                .apiKey(spec.apiKey())
                .completionsPath(completionsPath(spec))
                .build();
        var options = OpenAiChatOptions.builder()
                .model(spec.model())
                .internalToolExecutionEnabled(false)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    private static String completionsPath(ProviderSpec spec) {
        return "deepseek".equals(spec.name())
                ? "/chat/completions" : "/v1/chat/completions";
    }
}
