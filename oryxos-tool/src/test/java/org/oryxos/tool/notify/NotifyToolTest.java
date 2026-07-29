package org.oryxos.tool.notify;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.oryxos.core.model.ToolResult;
import org.oryxos.core.tool.ToolExecutionContext;

class NotifyToolTest {

    @TempDir Path workspace;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void usesConfiguredTargetAndPreservesRetryableFailure() throws Exception {
        var adapter = new NotifyChannelAdapter() {
            @Override public String channel() { return "test"; }
            @Override public ToolResult send(String target, String title, String message) {
                assertThat(target).isEqualTo("daily");
                assertThat(message).isEqualTo("hello");
                return ToolResult.failure("temporary transport failure", true);
            }
        };
        var tool = new NotifyTool(List.of(adapter), "test", mapper);
        var result = tool.execute(mapper.readTree("{\"message\":\"hello\"}"),
                new ToolExecutionContext("session", "agent", workspace,
                        Instant.now().plusSeconds(5), "scheduler", "user",
                        Map.of("notifyTarget", "daily")));

        assertThat(result.success()).isFalse();
        assertThat(result.retryable()).isTrue();
        assertThat(result.error()).doesNotContain("token", "secret");
    }

    @Test
    void requiresTarget() throws Exception {
        var tool = new NotifyTool(List.of(), "missing", mapper);
        var result = tool.execute(mapper.readTree("{\"message\":\"hello\"}"),
                new ToolExecutionContext("session", "agent", workspace,
                        Instant.now().plusSeconds(5), "test", "user", Map.of()));
        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("target");
    }
}
