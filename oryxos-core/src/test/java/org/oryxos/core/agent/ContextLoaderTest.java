package org.oryxos.core.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContextLoaderTest {

    @TempDir
    java.nio.file.Path temp;

    @Test
    void loadsAgentBodyBootstrapAndTimeInDeterministicOrder() throws Exception {
        Files.createDirectories(temp.resolve("agents/demo"));
        Files.writeString(temp.resolve("agents/demo/AGENT.md"), """
                ---
                provider: deepseek
                ---
                AGENT BODY
                """);
        Files.writeString(temp.resolve("AGENTS.md"), "AGENTS BOOTSTRAP");
        Files.writeString(temp.resolve("SOUL.md"), "SOUL BOOTSTRAP");
        Files.writeString(temp.resolve("USER.md"), "USER BOOTSTRAP");
        var definition = new AgentLoader(Set.of("deepseek"), Set.of(),
                Set.of("cli"), ZoneId.of("UTC")).load(temp).getFirst();

        var context = new ContextLoader(temp).load(definition,
                Instant.parse("2026-07-29T00:00:00Z"), ZoneId.of("Asia/Shanghai"));

        assertThat(context).containsSubsequence("AGENT BODY", "AGENTS BOOTSTRAP",
                "SOUL BOOTSTRAP", "USER BOOTSTRAP", "2026-07-29");
        assertThat(context).doesNotContain("provider: deepseek");
    }
}
