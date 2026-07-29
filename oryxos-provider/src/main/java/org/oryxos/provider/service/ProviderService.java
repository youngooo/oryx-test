package org.oryxos.provider.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.oryxos.core.model.LlmCallRecord;
import org.oryxos.core.port.ClockProvider;
import org.oryxos.core.port.InvocationAuditStore;
import org.oryxos.core.port.ProviderGateway;
import org.oryxos.provider.adapter.FunctionCallingAdapter;
import org.oryxos.provider.adapter.ProviderRequestMapper;
import org.oryxos.provider.adapter.ProviderResponseMapper;
import org.oryxos.provider.config.ProviderConfiguration;
import org.oryxos.provider.config.ProviderConfiguration.ProviderBinding;

public final class ProviderService implements ProviderGateway {

    private final Map<String, ProviderBinding> providers;
    private final InvocationAuditStore auditStore;
    private final ClockProvider clock;
    private final ProviderRequestMapper requestMapper;
    private final ProviderResponseMapper responseMapper;

    public ProviderService(Collection<ProviderBinding> bindings,
            InvocationAuditStore auditStore, ClockProvider clock) {
        this(bindings, auditStore, clock,
                new ProviderRequestMapper(new FunctionCallingAdapter()),
                new ProviderResponseMapper());
    }

    ProviderService(Collection<ProviderBinding> bindings,
            InvocationAuditStore auditStore, ClockProvider clock,
            ProviderRequestMapper requestMapper,
            ProviderResponseMapper responseMapper) {
        var explicit = new LinkedHashMap<String, ProviderBinding>();
        for (var binding : bindings) {
            if (explicit.putIfAbsent(binding.name(), binding) != null) {
                throw new IllegalStateException("Duplicate provider name: " + binding.name());
            }
        }
        this.providers = Map.copyOf(explicit);
        this.auditStore = java.util.Objects.requireNonNull(auditStore);
        this.clock = java.util.Objects.requireNonNull(clock);
        this.requestMapper = java.util.Objects.requireNonNull(requestMapper);
        this.responseMapper = java.util.Objects.requireNonNull(responseMapper);
    }

    @Override
    public Response generate(Request request) {
        var startedAt = clock.now();
        var binding = providers.get(request.provider());
        if (binding == null) {
            var completedAt = clock.now();
            saveFailure(request, requestedModel(request), "UNKNOWN_PROVIDER",
                    "Unknown provider: " + request.provider(), startedAt, completedAt);
            throw new ProviderException("Unknown provider: " + request.provider());
        }
        var model = request.model() == null || request.model().isBlank()
                ? binding.defaultModel() : request.model();
        try {
            var prompt = requestMapper.toPrompt(request, model);
            var mapped = responseMapper.from(binding.chatModel().call(prompt));
            var completedAt = clock.now();
            auditStore.saveLlmCall(new LlmCallRecord(UUID.randomUUID().toString(),
                    request.sessionId(), request.provider(), model, request.iteration(),
                    mapped.promptTokens(), mapped.completionTokens(), true,
                    mapped.finishReason(), null, null, startedAt, completedAt,
                    duration(startedAt, completedAt)));
            return mapped;
        } catch (Exception exception) {
            var completedAt = clock.now();
            var errorCode = categorize(exception);
            var safeMessage = ProviderConfiguration.redact(
                    safeMessage(exception), binding.secrets());
            saveFailure(request, model, errorCode, safeMessage, startedAt, completedAt);
            throw new ProviderException(
                    "Provider '" + request.provider() + "' call failed: " + safeMessage,
                    exception);
        }
    }

    private void saveFailure(Request request, String model, String errorCode,
            String message, Instant startedAt, Instant completedAt) {
        auditStore.saveLlmCall(new LlmCallRecord(UUID.randomUUID().toString(),
                request.sessionId(), request.provider(), model, request.iteration(),
                null, null, false, null, errorCode, message,
                startedAt, completedAt, duration(startedAt, completedAt)));
    }

    private String requestedModel(Request request) {
        return request.model() == null || request.model().isBlank()
                ? "unresolved" : request.model();
    }

    private String categorize(Exception exception) {
        if (exception instanceof java.net.http.HttpTimeoutException
                || exception instanceof java.util.concurrent.TimeoutException) {
            return "TIMEOUT";
        }
        if (exception instanceof IllegalArgumentException) {
            return "INVALID_RESPONSE";
        }
        return "PROVIDER_ERROR";
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private long duration(Instant start, Instant end) {
        return Math.max(0, Duration.between(start, end).toMillis());
    }

    public static final class ProviderException extends RuntimeException {
        public ProviderException(String message) {
            super(message);
        }

        public ProviderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
