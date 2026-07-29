package org.oryxos.web.api;

import java.time.Instant;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "success", data, Instant.now());
    }

    public static ApiResponse<Object> error(String code, String message) {
        return new ApiResponse<>(code, message, null, Instant.now());
    }
}
