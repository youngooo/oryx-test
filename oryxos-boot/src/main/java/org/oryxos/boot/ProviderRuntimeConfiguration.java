package org.oryxos.boot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.oryxos.core.port.ClockProvider;
import org.oryxos.core.port.InvocationAuditStore;
import org.oryxos.core.port.ProviderGateway;
import org.oryxos.provider.config.OpenAiCompatibleChatModelFactory;
import org.oryxos.provider.config.ProviderConfiguration;
import org.oryxos.provider.config.ProviderConfiguration.ProviderSpec;
import org.oryxos.provider.service.ProviderService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Explicit provider-name to ChatModel composition for the executable runtime.
 */
@Configuration
public class ProviderRuntimeConfiguration {

    @Bean
    ClockProvider clockProvider() {
        return ClockProvider.systemUtc();
    }

    @Bean
    @ConditionalOnMissingBean(ProviderGateway.class)
    ProviderGateway providerGateway(Environment environment,
            InvocationAuditStore auditStore, ClockProvider clock) {
        var specs = new ArrayList<ProviderSpec>();
        addConfigured(specs, environment, "deepseek");
        addConfigured(specs, environment, "kimi");

        var models = new LinkedHashMap<String, ChatModel>();
        for (var spec : specs) {
            models.put(spec.name(),
                    OpenAiCompatibleChatModelFactory.create(spec));
        }
        return new ProviderService(
                ProviderConfiguration.bind(specs, models),
                auditStore, clock);
    }

    private void addConfigured(List<ProviderSpec> specs,
            Environment environment, String name) {
        var prefix = "oryxos.provider." + name + ".";
        var apiKey = environment.getProperty(prefix + "api-key");
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }
        specs.add(new ProviderSpec(name,
                environment.getRequiredProperty(prefix + "model"),
                apiKey,
                environment.getRequiredProperty(prefix + "base-url")));
    }
}
