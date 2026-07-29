package org.oryxos.web.error;

import jakarta.validation.ConstraintViolationException;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeoutException;
import org.oryxos.web.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Object>> api(ApiException failure) {
        return response(failure.status(), failure.code(), failure.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    ResponseEntity<ApiResponse<Object>> validation(Exception failure) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Request validation failed");
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ApiResponse<Object>> missing(NoSuchElementException failure) {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND",
                safe(failure, "Requested resource was not found"));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiResponse<Object>> conflict(IllegalStateException failure) {
        return response(HttpStatus.CONFLICT, "SESSION_ARCHIVED",
                safe(failure, "Session state conflict"));
    }

    @ExceptionHandler(TimeoutException.class)
    ResponseEntity<ApiResponse<Object>> timeout(TimeoutException failure) {
        return response(HttpStatus.GATEWAY_TIMEOUT, "INVOCATION_TIMEOUT",
                "Agent invocation timed out");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Object>> internal(Exception failure) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected internal error occurred");
    }

    private ResponseEntity<ApiResponse<Object>> response(
            HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(ApiResponse.error(code, message));
    }

    private String safe(Exception failure, String fallback) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? fallback : failure.getMessage();
    }
}
