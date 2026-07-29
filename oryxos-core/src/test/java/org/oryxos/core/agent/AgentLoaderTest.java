package org.oryxos.core.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.oryxos.core.model.AgentDefinition;

class AgentLoaderTest {

    @TempDir
    java.nio.file.Path temp;

    @Test
    void derivesProfilesAndIsolatesInvalidAgents() throws Exception {
        writeAgent("valid", """
                ---
                provider: deepseek
                model: deepseek-chat
                tools: [http_get]
                channels: [cli]
                max_iterations: 4
                ---
                # Valid
                Answer briefly.
                """);
        writeAgent("invalid", """
                ---
                provider: missing
                ---
                # Invalid
                Should not block the valid agent.
                """);

        var loader = new AgentLoader(Set.of("deepseek"), Set.of("http_get"),
                Set.of("cli"), ZoneId.of("Asia/Shanghai"));
        var definitions = loader.load(temp);
        var registry = new ProfileRegistry();
        registry.reload(definitions);

        assertThat(definitions).hasSize(2);
        assertThat(definitions).filteredOn(d -> d.loadStatus()
                == AgentDefinition.LoadStatus.INVALID).hasSize(1);
        assertThat(registry.find("valid")).isPresent();
        assertThat(registry.find("invalid")).isEmpty();
        assertThat(registry.find("valid").orElseThrow().maxIterations()).isEqualTo(4);
    }

    @Test
    void reloadReplacesRegistrySnapshot() throws Exception {
        writeAgent("one", """
                ---
                provider: deepseek
                ---
                First body
                """);
        var loader = new AgentLoader(Set.of("deepseek"), Set.of(),
                Set.of("cli"), ZoneId.of("UTC"));
        var refreshed = new java.util.concurrent.atomic.AtomicInteger();
        var registry = new ProfileRegistry(profiles -> refreshed.incrementAndGet());
        registry.reload(loader.load(temp));
        assertThat(registry.list()).extracting("name").containsExactly("one");

        Files.move(temp.resolve("agents/one"), temp.resolve("agents/two"));
        registry.reload(loader.load(temp));
        assertThat(registry.list()).extracting("name").containsExactly("two");
        assertThat(refreshed).hasValue(2);
    }

    private void writeAgent(String name, String content) throws Exception {
        var directory = temp.resolve("agents").resolve(name);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("AGENT.md"), content);
    }
}
