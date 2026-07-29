package org.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.oryxos.core.model.Message;
import org.oryxos.core.port.ProviderGateway;
import org.oryxos.provider.adapter.FunctionCallingAdapter;
import org.oryxos.provider.adapter.ProviderRequestMapper;
import org.springframework.ai.chat.messages.AssistantMessage;

class ProviderRequestMapperTest {

    @Test
    void replaysPersistedAssistantToolCallsForTheNextProviderRound() {
        var request = new ProviderGateway.Request(
                "session-1", "deepseek", "deepseek-chat", 2,
                List.of(new Message("message-1", 1, Message.Role.ASSISTANT,
                        "Tool requests: weather-1:http_get", null, null,
                        List.of(new Message.ToolRequest(
                                "weather-1", "http_get",
                                "{\"url\":\"https://api.open-meteo.com\"}")),
                        Instant.parse("2026-07-29T00:00:00Z"))),
                List.of(), null);

        var prompt = new ProviderRequestMapper(new FunctionCallingAdapter())
                .toPrompt(request, "deepseek-chat");

        assertThat(prompt.getInstructions()).singleElement()
                .isInstanceOfSatisfying(AssistantMessage.class, assistant -> {
                    assertThat(assistant.getToolCalls()).singleElement()
                            .satisfies(call -> {
                                assertThat(call.id()).isEqualTo("weather-1");
                                assertThat(call.name()).isEqualTo("http_get");
                                assertThat(call.arguments())
                                        .contains("api.open-meteo.com");
                            });
                });
    }
}
