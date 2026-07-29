package org.oryxos.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.oryxos.core.port.Sandbox;
import org.oryxos.core.tool.ToolExecutionContext;
import org.oryxos.tool.sandbox.WhitelistSandbox;

class FileToolsTest {

    @TempDir Path workspace;
    @TempDir Path outside;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void writesReadsAndListsNormalizedWorkspacePaths() throws Exception {
        var sandbox = sandbox();
        sandbox.enforce(new Sandbox.Action("FILE", "notes/../notes/a.txt"));
        var context = context();
        var write = new WriteFileTool(mapper).execute(mapper.readTree(
                "{\"path\":\"notes/a.txt\",\"content\":\"hello\",\"createParents\":true}"),
                context);
        var read = new ReadFileTool(mapper).execute(mapper.readTree(
                "{\"path\":\"notes/../notes/a.txt\"}"), context);
        var list = new ListDirTool(mapper).execute(mapper.readTree(
                "{\"path\":\"notes\",\"recursive\":true}"), context);

        assertThat(write.success()).isTrue();
        assertThat(read.content()).isEqualTo("hello");
        assertThat(list.content()).contains("a.txt");
    }

    @Test
    void rejectsTraversalAndSymlinkEscapeAndReportsMissingFiles() throws Exception {
        var sandbox = sandbox();
        assertThatThrownBy(() -> sandbox.enforce(
                new Sandbox.Action("FILE", "../escape.txt")))
                .isInstanceOf(Sandbox.DeniedException.class);
        var missing = new ReadFileTool(mapper).execute(
                mapper.readTree("{\"path\":\"missing.txt\"}"), context());
        assertThat(missing.success()).isFalse();

        var link = workspace.resolve("outside-link");
        try {
            Files.createSymbolicLink(link, outside);
            assertThatThrownBy(() -> sandbox.enforce(
                    new Sandbox.Action("FILE", "outside-link/secret.txt")))
                    .isInstanceOf(Sandbox.DeniedException.class);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException ignored) {
            // Some Windows developer environments do not grant symlink creation.
        }
    }

    private WhitelistSandbox sandbox() {
        return new WhitelistSandbox(Set.of(), Set.of(), Set.of(workspace));
    }

    private ToolExecutionContext context() {
        return new ToolExecutionContext("session", "agent", workspace,
                Instant.now().plusSeconds(10), "test", "user", Map.of());
    }
}
