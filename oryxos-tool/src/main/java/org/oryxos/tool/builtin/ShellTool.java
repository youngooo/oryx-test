package org.oryxos.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.oryxos.core.model.ToolResult;
import org.oryxos.core.tool.OryxTool;
import org.oryxos.core.tool.ToolExecutionContext;

public final class ShellTool implements OryxTool {

    private static final int MAX_OUTPUT = 64 * 1024;
    private static final String META = ";&|`><\r\n";
    private final ObjectNode schema;

    public ShellTool(ObjectMapper mapper) {
        schema = FileToolSupport.schema(mapper);
        var properties = schema.putObject("properties");
        properties.putObject("command").put("type", "string");
        properties.putObject("args").put("type", "array")
                .putObject("items").put("type", "string");
        properties.putObject("workingDirectory").put("type", "string");
        properties.putObject("timeoutSeconds").put("type", "integer");
        schema.putArray("required").add("command").add("args");
    }

    @Override public String getName() { return "shell"; }
    @Override public String getDescription() {
        return "Run one allowlisted executable with structured arguments";
    }
    @Override public JsonNode getInputSchema() { return schema.deepCopy(); }

    @Override
    public ToolResult execute(JsonNode arguments, ToolExecutionContext context) {
        Process process = null;
        try {
            var command = arguments.path("command").asText().strip();
            if (unsafe(command) || !arguments.path("args").isArray()) {
                return ToolResult.failure("Compound shell syntax is not supported", false);
            }
            var tokens = new ArrayList<String>();
            tokens.add(command);
            for (var arg : arguments.path("args")) {
                if (!arg.isTextual() || arg.asText().indexOf('\0') >= 0
                        || arg.asText().contains("\r")
                        || arg.asText().contains("\n")) {
                    return ToolResult.failure("Unsafe shell argument", false);
                }
                tokens.add(arg.asText());
            }
            var directory = arguments.has("workingDirectory")
                    ? FileToolSupport.resolve(context.workspaceRoot(),
                            arguments.path("workingDirectory").asText())
                    : context.workspaceRoot();
            if (!directory.startsWith(context.workspaceRoot())
                    || !Files.isDirectory(directory)) {
                return ToolResult.failure("Working directory is outside the workspace", false);
            }
            var timeout = arguments.path("timeoutSeconds").asInt(30);
            if (timeout < 1 || timeout > 60) {
                return ToolResult.failure("timeoutSeconds must be between 1 and 60", false);
            }
            var builder = new ProcessBuilder(tokens).directory(directory.toFile())
                    .redirectErrorStream(true);
            builder.environment().keySet().removeIf(ShellTool::secretEnvironmentName);
            process = builder.start();
            var running = process;
            var output = new LimitedOutputStream(MAX_OUTPUT);
            try (var reader = Executors.newVirtualThreadPerTaskExecutor()) {
                var drain = reader.submit(() -> {
                    running.getInputStream().transferTo(output);
                    return null;
                });
                if (!running.waitFor(timeout, TimeUnit.SECONDS)) {
                    running.destroyForcibly();
                    drain.cancel(true);
                    return ToolResult.failure("Shell command timed out", true);
                }
                drain.get(2, TimeUnit.SECONDS);
            }
            var text = output.text();
            return process.exitValue() == 0
                    ? ToolResult.success(text.isBlank() ? "(no output)" : text)
                    : ToolResult.failure("Shell command exited with code "
                            + process.exitValue() + ": " + text, false);
        } catch (Exception failure) {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            return ToolResult.failure("Shell command failed: "
                    + failure.getClass().getSimpleName(), false);
        }
    }

    private static boolean unsafe(String value) {
        return value == null || value.isBlank()
                || value.chars().anyMatch(ch -> META.indexOf(ch) >= 0);
    }

    private static boolean secretEnvironmentName(String name) {
        var normalized = name.toUpperCase(Locale.ROOT);
        return normalized.contains("KEY") || normalized.contains("TOKEN")
                || normalized.contains("SECRET") || normalized.contains("PASSWORD");
    }

    private static final class LimitedOutputStream extends OutputStream {
        private final int limit;
        private final ByteArrayOutputStream kept = new ByteArrayOutputStream();
        private boolean truncated;

        private LimitedOutputStream(int limit) {
            this.limit = limit;
        }

        @Override
        public void write(int value) {
            if (kept.size() < limit) {
                kept.write(value);
            } else {
                truncated = true;
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            var remaining = Math.max(0, limit - kept.size());
            var accepted = Math.min(remaining, length);
            kept.write(bytes, offset, accepted);
            truncated |= accepted < length;
        }

        private String text() {
            return kept.toString(StandardCharsets.UTF_8)
                    + (truncated ? "\n[truncated]" : "");
        }
    }
}
