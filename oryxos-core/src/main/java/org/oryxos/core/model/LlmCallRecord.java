package org.oryxos.core.model;

import java.time.Instant;

public record LlmCallRecord(
        String id,
        String sessionId,
        String provider,
        String model,
        int iteration,
        Long promptTokens,
        Long completionTokens,
        boolean success,
        String finishReason,
        String errorCode,
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        long durationMs) {

    public LlmCallRecord {
        id = Profile.requireText(id, "id");
        sessionId = Profile.requireText(sessionId, "sessionId");
        provider = Profile.requireText(provider, "provider");
        model = Profile.requireText(model, "model");
        if (iteration < 1) {
            throw new IllegalArgumentException("iteration must be positive");
        }
        requireNonNegative(promptTokens, "promptTokens");
        requireNonNegative(completionTokens, "completionTokens");
        if (startedAt == null || completedAt == null || completedAt.isBefore(startedAt)
                || durationMs < 0) {
            throw new IllegalArgumentException("Invalid LLM timing");
        }
        finishReason = Profile.normalize(finishReason);
        errorCode = Profile.normalize(errorCode);
        errorMessage = Profile.normalize(errorMessage);
        if (success && errorCode != null) {
            throw new IllegalArgumentException("Successful call cannot have an error");
        }
        if (!success && errorCode == null) {
            throw new IllegalArgumentException("Failed call requires an error code");
        }
    }

    private static void requireNonNegative(Long value, String field) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }
}
