package org.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.oryxos.core.model.LlmCallRecord;
import org.oryxos.core.model.ToolInvocationRecord;
import org.oryxos.core.port.InvocationAuditStore;
import org.oryxos.core.port.ProviderGateway;
import org.oryxos.provider.config.ProviderConfiguration.ProviderBinding;
import org.oryxos.provider.service.ProviderService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

class ProviderServiceTest {

    @Test
    void mapsDeepSeekAndKimiByExplicitName() {
        var audit = new CapturingAuditStore();
        var service = new ProviderService(List.of(
                binding("deepseek", "deepseek-chat", "deepseek-response"),
                binding("kimi", "moonshot-v1-8k", "kimi-response")),
                audit, tickingClock());

        assertThat(service.generate(request("deepseek")).content())
                .isEqualTo("deepseek-response");
        assertThat(service.generate(request("kimi")).content())
                .isEqualTo("kimi-response");
        assertThat(audit.llmCalls).extracting(LlmCallRecord::provider)
                .containsExactly("deepseek", "kimi");
    }

    @Test
    void rejectsUnknownAndDuplicateProviders() {
        var audit = new CapturingAuditStore();
        assertThatThrownBy(() -> new ProviderService(List.of(
                binding("deepseek", "m1", "one"),
                binding("deepseek", "m2", "two")), audit, tickingClock()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");

        var service = new ProviderService(List.of(binding(
                "deepseek", "deepseek-chat", "ok")), audit, tickingClock());
        assertThatThrownBy(() -> service.generate(request("missing")))
                .isInstanceOf(ProviderService.ProviderException.class)
                .hasMessageContaining("Unknown provider");
    }

    @Test
    void providerFailureNeverFallsBack() {
        var audit = new CapturingAuditStore();
        ChatModel failing = prompt -> {
            throw new IllegalStateException("upstream failed secret-123");
        };
        var service = new ProviderService(List.of(
                new ProviderBinding("deepseek", "deepseek-chat", failing,
                        List.of("secret-123")),
                binding("kimi", "moonshot-v1-8k", "must-not-run")),
                audit, tickingClock());

        assertThatThrownBy(() -> service.generate(request("deepseek")))
                .isInstanceOf(ProviderService.ProviderException.class)
                .hasMessageNotContaining("secret-123");
        assertThat(audit.llmCalls).singleElement().satisfies(call -> {
            assertThat(call.provider()).isEqualTo("deepseek");
            assertThat(call.success()).isFalse();
            assertThat(call.errorMessage()).doesNotContain("secret-123");
        });
    }

    private ProviderBinding binding(String name, String model, String response) {
        return new ProviderBinding(name, model,
                prompt -> new ChatResponse(List.of(
                        new Generation(new AssistantMessage(response)))),
                List.of());
    }

    private ProviderGateway.Request request(String provider) {
        return new ProviderGateway.Request("session-1", provider, null, 1,
                List.of(), List.of(), null);
    }

    private org.oryxos.core.port.ClockProvider tickingClock() {
        var times = new java.util.concurrent.atomic.AtomicLong();
        return () -> Instant.EPOCH.plusMillis(times.getAndIncrement() * 10);
    }

    static class CapturingAuditStore implements InvocationAuditStore {
        final List<LlmCallRecord> llmCalls = new ArrayList<>();
        @Override public void saveLlmCall(LlmCallRecord record) { llmCalls.add(record); }
        @Override public void saveToolInvocation(ToolInvocationRecord record) { }
        @Override public Page<LlmCallRecord> findLlmCalls(String id, int offset, int limit) {
            return new Page<>(llmCalls, offset, limit, llmCalls.size());
        }
        @Override public Page<ToolInvocationRecord> findToolInvocations(
                String id, int offset, int limit) {
            return new Page<>(List.of(), offset, limit, 0);
        }
    }
}
