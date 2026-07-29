package org.oryxos.core.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.oryxos.core.model.LlmCallRecord;
import org.oryxos.core.model.ToolInvocationRecord;
import org.oryxos.core.model.ToolResult;
import org.oryxos.core.port.InvocationAuditStore;
import org.oryxos.core.port.Sandbox;
import org.oryxos.core.port.ToolCatalog;

class ToolExecutorTest {

    @TempDir Path workspace;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void validatesArgumentsAndAuditsUnknownAndSandboxRejectedCallsOnce() {
        var audit = new Audit();
        var tool = tool("http_get", arguments -> ToolResult.success("ok"));
        var executor = new ToolExecutor(catalog(tool),
                action -> { throw new Sandbox.DeniedException("denied"); },
                audit, Instant::now, mapper, 0);

        assertThat(executor.execute("missing", "{}", context()).success()).isFalse();
        assertThat(executor.execute("http_get", "{}", context()).success()).isFalse();
        assertThat(executor.execute("http_get",
                "{\"url\":\"https://weather.test\"}", context()).success()).isFalse();
        assertThat(audit.tools).hasSize(3);
        assertThat(audit.tools).extracting(ToolInvocationRecord::errorCode)
                .containsExactly("UNKNOWN_TOOL", "INVALID_ARGUMENT", "SANDBOX_DENIED");
    }

    @Test
    void boundsRetryAndProducesOneAggregateAuditRecord() {
        var attempts = new AtomicInteger();
        var audit = new Audit();
        var tool = tool("http_get", arguments -> {
            attempts.incrementAndGet();
            return ToolResult.failure("temporary", true);
        });
        var executor = new ToolExecutor(catalog(tool), action -> { },
                audit, Instant::now, mapper, 2);

        var result = executor.execute("http_get",
                "{\"url\":\"https://weather.test\"}", context());

        assertThat(result.success()).isFalse();
        assertThat(attempts).hasValue(3);
        assertThat(audit.tools).singleElement()
                .extracting(ToolInvocationRecord::errorCode)
                .isEqualTo("RETRY_EXHAUSTED");
    }

    @Test
    void enforcesDeadlineTimeout() {
        var audit = new Audit();
        var executor = new ToolExecutor(catalog(tool("http_get", arguments -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return ToolResult.success("late");
        })), action -> { }, audit, Instant::now, mapper, 0);
        var context = new ToolExecutionContext("session-1", "weather",
                workspace, Instant.now().plusMillis(20), "cli", "user", Map.of());

        assertThat(executor.execute("http_get",
                "{\"url\":\"https://weather.test\"}", context).success()).isFalse();
        assertThat(audit.tools.getFirst().errorCode()).isEqualTo("TIMEOUT");
    }

    @Test
    void recursivelyRedactsSecretBearingHeadersFromAudit() {
        var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.putObject("properties").putObject("headers").put("type", "object");
        var tool = new OryxTool() {
            @Override public String getName() { return "http_post"; }
            @Override public String getDescription() { return "post"; }
            @Override public JsonNode getInputSchema() { return schema; }
            @Override public ToolResult execute(JsonNode arguments,
                    ToolExecutionContext context) {
                return ToolResult.success("ok");
            }
        };
        var audit = new Audit();
        var executor = new ToolExecutor(catalog(tool), action -> { },
                audit, Instant::now, mapper, 0);

        executor.execute("http_post",
                "{\"headers\":{\"Authorization\":\"do-not-store\","
                        + "\"X-Trace\":\"safe\"}}", context());

        assertThat(audit.tools.getFirst().argumentsJson())
                .contains("\"Authorization\":\"[REDACTED]\"")
                .contains("\"X-Trace\":\"safe\"")
                .doesNotContain("do-not-store");
    }

    private ToolCatalog catalog(OryxTool tool) {
        return new ToolCatalog() {
            @Override public Optional<OryxTool> find(String name) {
                return tool.getName().equals(name) ? Optional.of(tool) : Optional.empty();
            }
            @Override public List<OryxTool> availableTo(Set<String> names) {
                return names.contains(tool.getName()) ? List.of(tool) : List.of();
            }
        };
    }

    private OryxTool tool(String name,
            java.util.function.Function<JsonNode, ToolResult> behavior) {
        var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.putObject("properties").putObject("url").put("type", "string");
        schema.putArray("required").add("url");
        return new OryxTool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return "fetch"; }
            @Override public JsonNode getInputSchema() { return schema; }
            @Override public ToolResult execute(JsonNode arguments,
                    ToolExecutionContext context) {
                return behavior.apply(arguments);
            }
        };
    }

    private ToolExecutionContext context() {
        return new ToolExecutionContext("session-1", "weather", workspace,
                Instant.now().plusSeconds(2), "cli", "user", Map.of());
    }

    private static class Audit implements InvocationAuditStore {
        final List<ToolInvocationRecord> tools = new ArrayList<>();
        @Override public void saveLlmCall(LlmCallRecord record) { }
        @Override public void saveToolInvocation(ToolInvocationRecord record) {
            tools.add(record);
        }
        @Override public Page<LlmCallRecord> findLlmCalls(
                String id, int offset, int limit) {
            return new Page<>(List.of(), offset, limit, 0);
        }
        @Override public Page<ToolInvocationRecord> findToolInvocations(
                String id, int offset, int limit) {
            return new Page<>(tools, offset, limit, tools.size());
        }
    }
}
