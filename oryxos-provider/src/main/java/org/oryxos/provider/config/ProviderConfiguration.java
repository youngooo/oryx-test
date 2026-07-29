package org.oryxos.provider.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Builds an explicit provider-name to ChatModel mapping.
 */
public final class ProviderConfiguration {

    private static final Pattern CREDENTIAL_PATTERN = Pattern.compile(
            "(?i)(api[_-]?key|authorization|password|token)\\s*[=:]\\s*([^\\s,;]+)");

    private ProviderConfiguration() {
    }

    public static List<ProviderBinding> bind(
            Collection<ProviderSpec> specs, Map<String, ChatModel> models) {
        var bindings = new LinkedHashMap<String, ProviderBinding>();
        for (var spec : specs) {
            validateSpec(spec);
            var model = models.get(spec.name());
            if (model == null) {
                throw new IllegalStateException(
                        "No ChatModel is explicitly mapped for provider " + spec.name());
            }
            var binding = new ProviderBinding(spec.name(), spec.model(), model,
                    List.of(spec.apiKey()));
            if (bindings.putIfAbsent(spec.name(), binding) != null) {
                throw new IllegalStateException("Duplicate provider name: " + spec.name());
            }
        }
        if (!models.keySet().equals(bindings.keySet())) {
            var extras = new java.util.TreeSet<>(models.keySet());
            extras.removeAll(bindings.keySet());
            throw new IllegalStateException("ChatModel has no provider specification: " + extras);
        }
        return List.copyOf(bindings.values());
    }

    static void validateSpec(ProviderSpec spec) {
        if (spec == null || blank(spec.name()) || blank(spec.model())
                || blank(spec.baseUrl())) {
            throw new IllegalStateException("Provider name, model, and base URL are required");
        }
        if (blank(spec.apiKey()) || spec.apiKey().contains("${")) {
            throw new IllegalStateException(
                    "Provider " + spec.name() + " credential is missing or unresolved");
        }
        try {
            var uri = java.net.URI.create(spec.baseUrl());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Provider " + spec.name() + " base URL must be HTTPS");
        }
    }

    public static String redact(String value, Collection<String> knownSecrets) {
        if (value == null) {
            return null;
        }
        var result = value;
        for (var secret : knownSecrets == null ? List.<String>of() : knownSecrets) {
            if (!blank(secret)) {
                result = result.replace(secret, "[REDACTED]");
            }
        }
        return CREDENTIAL_PATTERN.matcher(result).replaceAll("$1=[REDACTED]");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record ProviderSpec(
            String name,
            String model,
            String apiKey,
            String baseUrl) {
    }

    public record ProviderBinding(
            String name,
            String defaultModel,
            ChatModel chatModel,
            List<String> secrets) {

        public ProviderBinding {
            if (blank(name) || blank(defaultModel) || chatModel == null) {
                throw new IllegalArgumentException(
                        "Provider binding name, model, and ChatModel are required");
            }
            name = name.trim();
            defaultModel = defaultModel.trim();
            secrets = List.copyOf(secrets == null ? List.of() : secrets);
        }
    }
}
