package org.oryxos.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CoreModelTest {

    private static final Instant NOW = Instant.parse("2026-07-29T00:00:00Z");

    @Test
    void validatesProfileAndCopiesCollections() {
        var tools = new java.util.LinkedHashSet<>(Set.of("http_get"));
        var profile = new Profile("weather", "deepseek", "deepseek-chat",
                tools, Set.of("cli"), null, List.of(), 10, 0.2);
        tools.add("shell");

        assertThat(profile.tools()).containsExactly("http_get");
        assertThatThrownBy(() -> new Profile("bad name", "deepseek", null,
                Set.of(), Set.of("cli"), null, List.of(), 10, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Profile("ok", "deepseek", null,
                Set.of(), Set.of("cli"), null, List.of(), 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Profile("ok", "deepseek", null,
                Set.of(), Set.of("cli"), null, List.of(), 11, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sessionRequiresOrderedMessagesAndArchiveConsistency() {
        var first = new Message("m1", 1, Message.Role.USER, "hello",
                null, null, NOW);
        var second = new Message("m2", 2, Message.Role.ASSISTANT, "hi",
                null, null, NOW.plusSeconds(1));
        var session = new Session("s1", "weather", "cli", "u1",
                List.of(first, second), Session.Status.ACTIVE, NOW,
                NOW.plusSeconds(1), null, 0);

        assertThat(session.messages()).containsExactly(first, second);
        assertThatThrownBy(() -> new Session("s1", "weather", "cli", "u1",
                List.of(second, first), Session.Status.ACTIVE, NOW, NOW, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Session("s1", "weather", "cli", "u1",
                List.of(), Session.Status.ARCHIVED, NOW, NOW, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toolResultAndAuditValuesRejectContradictions() {
        assertThat(ToolResult.success("ok").success()).isTrue();
        assertThat(ToolResult.failure("denied", false).retryable()).isFalse();
        assertThatThrownBy(() -> new ToolResult(true, "ok", "bad", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LlmCallRecord("id", "s", "deepseek",
                "model", 0, null, null, true, "STOP", null, null,
                NOW, NOW, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ToolInvocationRecord("id", "s", "shell",
                "{}", false, null, null, null, false, NOW, NOW, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
