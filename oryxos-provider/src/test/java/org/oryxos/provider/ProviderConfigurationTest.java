package org.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.oryxos.provider.config.ProviderConfiguration;
import org.oryxos.provider.config.ProviderConfiguration.ProviderSpec;
import org.springframework.ai.chat.model.ChatModel;

class ProviderConfigurationTest {

    private final ChatModel stub = prompt -> null;

    @Test
    void rejectsMissingAndUnresolvedCredentials() {
        assertThatThrownBy(() -> ProviderConfiguration.bind(
                List.of(new ProviderSpec("deepseek", "model", "", "https://example")),
                Map.of("deepseek", stub)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("credential")
                .hasMessageNotContaining("api-key");

        assertThatThrownBy(() -> ProviderConfiguration.bind(
                List.of(new ProviderSpec("kimi", "model", "${KIMI_API_KEY}",
                        "https://example")), Map.of("kimi", stub)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("credential")
                .hasMessageNotContaining("${KIMI_API_KEY}");
    }

    @Test
    void redactsKnownSecretsAndCredentialPatterns() {
        var redacted = ProviderConfiguration.redact(
                "Bearer secret-value api_key=abc123 password=hunter2",
                List.of("secret-value"));
        assertThat(redacted).doesNotContain("secret-value", "abc123", "hunter2")
                .contains("[REDACTED]");
    }

    @Test
    void requiresAnExactModelForEveryNamedSpec() {
        assertThatThrownBy(() -> ProviderConfiguration.bind(
                List.of(new ProviderSpec("deepseek", "model", "secret", "https://example")),
                Map.of("kimi", stub)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deepseek");
    }
}
