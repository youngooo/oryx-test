package org.oryxos.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.oryxos.core.port.Sandbox;
import org.oryxos.core.tool.ToolExecutionContext;
import org.oryxos.tool.sandbox.WhitelistSandbox;

class ShellToolTest {

    @TempDir Path workspace;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void runsAllowlistedExecutableWithStructuredArgumentsAndWorkingDirectory()
            throws Exception {
        var java = Path.of(System.getProperty("java.home"), "bin",
                isWindows() ? "java.exe" : "java").toString();
        var sandbox = new WhitelistSandbox(Set.of(),
                Set.of(isWindows() ? "java.exe" : "java"), Set.of(workspace));
        sandbox.enforce(new Sandbox.Action("SHELL", java));
        var tool = new ShellTool(mapper);
        var result = tool.execute(mapper.createObjectNode()
                .put("command", java)
                .put("workingDirectory", workspace.toString())
                .put("timeoutSeconds", 10)
                .set("args", mapper.createArrayNode().add("-version")), context());

        assertThat(result.success()).isTrue();
        assertThat(result.content()).containsIgnoringCase("version");
    }

    @Test
    void rejectsUnapprovedExecutableAndCompoundArguments() throws Exception {
        var sandbox = new WhitelistSandbox(Set.of(), Set.of("java"), Set.of(workspace));
        assertThatThrownBy(() -> sandbox.enforce(
                new Sandbox.Action("SHELL", "not-approved")))
                .isInstanceOf(Sandbox.DeniedException.class);
        var result = new ShellTool(mapper).execute(mapper.readTree(
                "{\"command\":\"java;whoami\",\"args\":[]}"), context());
        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("Compound");
    }

    @Test
    void enforcesTimeoutAndOutputLimit() throws Exception {
        var java = Path.of(System.getProperty("java.home"), "bin",
                isWindows() ? "java.exe" : "java").toString();
        var slowSource = workspace.resolve("Slow.java");
        Files.writeString(slowSource, """
                class Slow {
                    public static void main(String[] args) throws Exception {
                        Thread.sleep(3000);
                    }
                }
                """);
        var printSource = workspace.resolve("PrintMany.java");
        Files.writeString(printSource, """
                class PrintMany {
                    public static void main(String[] args) {
                        System.out.print("x".repeat(70000));
                    }
                }
                """);
        var tool = new ShellTool(mapper);
        var timedOut = tool.execute(mapper.createObjectNode()
                .put("command", java).put("timeoutSeconds", 1)
                .set("args", mapper.createArrayNode()
                        .add(slowSource.toString())), context());
        var bounded = tool.execute(mapper.createObjectNode()
                .put("command", java).put("timeoutSeconds", 10)
                .set("args", mapper.createArrayNode()
                        .add(printSource.toString())), context());

        assertThat(timedOut.success()).isFalse();
        assertThat(timedOut.retryable()).isTrue();
        assertThat(bounded.success()).isTrue();
        assertThat(bounded.content()).endsWith("[truncated]")
                .hasSizeLessThanOrEqualTo(64 * 1024 + 12);
    }

    private ToolExecutionContext context() {
        return new ToolExecutionContext("session", "agent", workspace,
                Instant.now().plusSeconds(20), "test", "user", Map.of());
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
