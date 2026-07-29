package org.oryxos.core.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceInitializerTest {

    @TempDir
    java.nio.file.Path temp;

    @Test
    void initializesExpectedLayoutAndIsIdempotent() throws Exception {
        var initializer = new WorkspaceInitializer();
        var workspace = temp.resolve(".oryxos");

        initializer.initialize(workspace);
        var agentFile = workspace.resolve("agents/default/AGENT.md");
        Files.writeString(agentFile, Files.readString(agentFile) + "\ncustom");
        initializer.initialize(workspace);

        assertThat(workspace.resolve("memory/MEMORY.md")).isRegularFile();
        assertThat(workspace.resolve("logs")).isDirectory();
        assertThat(workspace.resolve("mcp_servers.yaml")).isRegularFile();
        assertThat(workspace.resolve("oryxos.db")).isRegularFile();
        assertThat(Files.readString(agentFile)).endsWith("custom");
    }

    @Test
    void completesPartialWorkspaceButRejectsConflictingPathType() throws Exception {
        var initializer = new WorkspaceInitializer();
        var partial = temp.resolve("partial");
        Files.createDirectories(partial);
        Files.writeString(partial.resolve("SOUL.md"), "existing");
        initializer.initialize(partial);
        assertThat(partial.resolve("agents/default/AGENT.md")).isRegularFile();

        var conflict = temp.resolve("conflict");
        Files.createDirectories(conflict);
        Files.writeString(conflict.resolve("agents"), "not a directory");
        assertThatThrownBy(() -> initializer.initialize(conflict))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agents");
    }
}
