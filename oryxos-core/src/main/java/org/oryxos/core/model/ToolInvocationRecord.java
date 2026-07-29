package org.oryxos.core.model;

import java.time.Instant;

public record ToolInvocationRecord(
        String id,
        String sessionId,
        String toolName,
        String argumentsJson,
        boolean success,
        String resultSummary,
        String errorCode,
        String errorMessage,
        boolean retryable,
        Instant startedAt,
        Instant completedAt,
        long durationMs) {

    public ToolInvocationRecord {
        id = Profile.requireText(id, "id");
        sessionId = Profile.requireText(sessionId, "sessionId");
        toolName = Profile.requireText(toolName, "toolName");
        argumentsJson = Profile.requireText(argumentsJson, "argumentsJson");
        resultSummary = Profile.normalize(resultSummary);
        errorCode = Profile.normalize(errorCode);
        errorMessage = Profile.normalize(errorMessage);
        if (startedAt == null || completedAt == null || completedAt.isBefore(startedAt)
                || durationMs < 0) {
            throw new IllegalArgumentException("Invalid Tool timing");
        }
        if (success && errorCode != null) {
            throw new IllegalArgumentException("Successful invocation cannot have an error");
        }
        if (!success && errorCode == null) {
            throw new IllegalArgumentException("Failed invocation requires an error code");
        }
    }
}
