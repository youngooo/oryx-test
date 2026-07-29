package org.oryxos.core.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.oryxos.core.model.ToolInvocationRecord;
import org.oryxos.core.model.ToolResult;
import org.oryxos.core.port.ClockProvider;
import org.oryxos.core.port.InvocationAuditStore;
import org.oryxos.core.port.Sandbox;
import org.oryxos.core.port.ToolCatalog;

/**
 * The only execution pipeline used by ReAct. Spring AI never calls this class.
 */
public class ToolExecutor {

    private static final Set<String> SECRET_FIELDS = Set.of(
            "authorization", "api_key", "apikey", "token", "password", "secret");

    private final ToolCatalog catalog;
    private final Sandbox sandbox;
    private final InvocationAuditStore auditStore;
    private final ClockProvider clock;
    private final ObjectMapper objectMapper;
    private final int maxRetries;

    public ToolExecutor(ToolCatalog catalog, Sandbox sandbox,
            InvocationAuditStore auditStore, ClockProvider clock,
            ObjectMapper objectMapper) {
        this(catalog, sandbox, auditStore, clock, objectMapper, 3);
    }

    public ToolExecutor(ToolCatalog catalog, Sandbox sandbox,
            InvocationAuditStore auditStore, ClockProvider clock,
            ObjectMapper objectMapper, int maxRetries) {
        this.catalog = java.util.Objects.requireNonNull(catalog);
        this.sandbox = java.util.Objects.requireNonNull(sandbox);
        this.auditStore = java.util.Objects.requireNonNull(auditStore);
        this.clock = java.util.Objects.requireNonNull(clock);
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper);
        if (maxRetries < 0 || maxRetries > 3) {
            throw new IllegalArgumentException("maxRetries must be between 0 and 3");
        }
        this.maxRetries = maxRetries;
    }

    public ToolResult execute(String toolName, String argumentsJson,
            ToolExecutionContext context) {
        var startedAt = clock.now();
        ToolResult result;
        String errorCode = null;
        JsonNode arguments = null;
        try {
            var tool = catalog.find(toolName).orElseThrow(
                    () -> new ToolFailure("UNKNOWN_TOOL", "Unknown tool: " + toolName, false));
            arguments = parse(argumentsJson);
            validate(tool.getInputSchema(), arguments);
            sandbox.enforce(action(toolName, arguments));
            result = executeWithRetry(tool, arguments, context);
            if (!result.success()) {
                errorCode = result.retryable() ? "RETRY_EXHAUSTED" : "TOOL_ERROR";
            }
        } catch (ToolFailure failure) {
            errorCode = failure.code;
            result = ToolResult.failure(failure.getMessage(), failure.retryable);
        } catch (Sandbox.DeniedException denied) {
            errorCode = "SANDBOX_DENIED";
            result = ToolResult.failure(safe(denied.getMessage()), false);
        } catch (Exception exception) {
            errorCode = exception instanceof TimeoutException ? "TIMEOUT" : "TOOL_ERROR";
            result = ToolResult.failure(safe(exception.getMessage()),
                    exception instanceof TimeoutException);
        }
        var completedAt = clock.now();
        audit(toolName, argumentsJson, arguments, result, errorCode,
                startedAt, completedAt, context.sessionId());
        return result;
    }

    private ToolResult executeWithRetry(OryxTool tool, JsonNode arguments,
            ToolExecutionContext context) throws Exception {
        ToolResult result = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            result = executeOnce(tool, arguments, context);
            if (result.success() || !result.retryable()) {
                return result;
            }
        }
        return result;
    }

    private ToolResult executeOnce(OryxTool tool, JsonNode arguments,
            ToolExecutionContext context) throws Exception {
        var remaining = Duration.between(clock.now(), context.deadline()).toMillis();
        if (remaining <= 0) {
            throw new TimeoutException("Tool invocation deadline exceeded");
        }
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = executor.submit(() -> tool.execute(arguments, context));
            try {
                var result = future.get(remaining, TimeUnit.MILLISECONDS);
                if (result == null) {
                    throw new ToolFailure("MALFORMED_RESULT",
                            "Tool returned no result", false);
                }
                return result;
            } catch (java.util.concurrent.TimeoutException timeout) {
                future.cancel(true);
                throw new TimeoutException("Tool invocation timed out");
            }
        }
    }

    private JsonNode parse(String json) {
        try {
            var value = objectMapper.readTree(json == null ? "{}" : json);
            if (value == null || !value.isObject()) {
                throw new ToolFailure("INVALID_ARGUMENT",
                        "Tool arguments must be a JSON object", false);
            }
            return value;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new ToolFailure("INVALID_ARGUMENT",
                    "Malformed Tool arguments", false);
        }
    }

    private void validate(JsonNode schema, JsonNode arguments) {
        var required = schema.path("required");
        if (required.isArray()) {
            required.forEach(field -> {
                if (!arguments.has(field.asText())) {
                    throw new ToolFailure("INVALID_ARGUMENT",
                            "Missing required argument: " + field.asText(), false);
                }
            });
        }
        if (schema.path("additionalProperties").isBoolean()
                && !schema.path("additionalProperties").asBoolean()) {
            var properties = schema.path("properties");
            Iterator<String> names = arguments.fieldNames();
            while (names.hasNext()) {
                var name = names.next();
                if (!properties.has(name)) {
                    throw new ToolFailure("INVALID_ARGUMENT",
                            "Unknown argument: " + name, false);
                }
            }
        }
        var properties = schema.path("properties");
        arguments.fields().forEachRemaining(entry -> {
            var expected = properties.path(entry.getKey()).path("type").asText();
            if (!expected.isBlank() && !matches(expected, entry.getValue())) {
                throw new ToolFailure("INVALID_ARGUMENT",
                        "Invalid type for argument: " + entry.getKey(), false);
            }
        });
    }

    private boolean matches(String type, JsonNode value) {
        return switch (type) {
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "array" -> value.isArray();
            case "object" -> value.isObject();
            default -> true;
        };
    }

    private Sandbox.Action action(String toolName, JsonNode arguments) {
        if (toolName.startsWith("http_")) {
            return new Sandbox.Action("HTTP", arguments.path("url").asText());
        }
        if (Set.of("read_file", "write_file", "list_dir").contains(toolName)) {
            return new Sandbox.Action("FILE", arguments.path("path").asText());
        }
        if ("shell".equals(toolName)) {
            return new Sandbox.Action("SHELL", arguments.path("command").asText());
        }
        return new Sandbox.Action("TOOL", toolName);
    }

    private void audit(String toolName, String originalJson, JsonNode parsed,
            ToolResult result, String errorCode, Instant startedAt,
            Instant completedAt, String sessionId) {
        auditStore.saveToolInvocation(new ToolInvocationRecord(
                UUID.randomUUID().toString(), sessionId, toolName,
                redact(parsed, originalJson), result.success(),
                result.success() ? bounded(result.content()) : null,
                result.success() ? null : errorCode,
                result.success() ? null : bounded(result.error()),
                result.retryable(), startedAt, completedAt,
                Math.max(0, Duration.between(startedAt, completedAt).toMillis())));
    }

    private String redact(JsonNode parsed, String originalJson) {
        if (parsed == null || !parsed.isObject()) {
            return "{}";
        }
        var copy = parsed.deepCopy();
        redactNode(copy);
        try {
            return objectMapper.writeValueAsString(copy);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private void redactNode(JsonNode node) {
        if (node.isObject()) {
            var object = (com.fasterxml.jackson.databind.node.ObjectNode) node;
            var fields = new java.util.ArrayList<String>();
            object.fieldNames().forEachRemaining(fields::add);
            for (var field : fields) {
                if (SECRET_FIELDS.contains(field.toLowerCase())) {
                    object.put(field, "[REDACTED]");
                } else {
                    redactNode(object.get(field));
                }
            }
        } else if (node.isArray()) {
            node.forEach(this::redactNode);
        }
    }

    private String bounded(String value) {
        var safe = safe(value);
        return safe.length() <= 4096 ? safe : safe.substring(0, 4096);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "Tool execution failed" : value;
    }

    private static final class ToolFailure extends RuntimeException {
        private final String code;
        private final boolean retryable;

        private ToolFailure(String code, String message, boolean retryable) {
            super(message);
            this.code = code;
            this.retryable = retryable;
        }
    }
}
