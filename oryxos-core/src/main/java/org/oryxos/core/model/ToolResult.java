package org.oryxos.core.model;

public record ToolResult(boolean success, String content, String error, boolean retryable) {

    public ToolResult {
        content = Profile.normalize(content);
        error = Profile.normalize(error);
        if (success && error != null) {
            throw new IllegalArgumentException("Successful result cannot contain an error");
        }
        if (!success && error == null) {
            throw new IllegalArgumentException("Failed result requires an error");
        }
    }

    public static ToolResult success(String content) {
        return new ToolResult(true, content, null, false);
    }

    public static ToolResult failure(String error, boolean retryable) {
        return new ToolResult(false, null, error, retryable);
    }
}
