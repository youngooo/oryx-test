package org.oryxos.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.oryxos.cli.output.CliOutput;
import org.oryxos.cli.runtime.RuntimeLauncher;
import picocli.CommandLine;

class OryxOsCommandTest {

    @TempDir
    Path temp;

    @Test
    void exposesExactlyTwelveContractCommands() {
        var command = OryxOsCommand.commandLine(new StubLauncher());
        var leaves = command.getSubcommands().values().stream()
                .mapToLong(child -> child.getSubcommands().isEmpty()
                        ? 1 : child.getSubcommands().size())
                .sum();

        assertThat(leaves).isEqualTo(12);
        assertThat(command.getSubcommands().keySet()).containsExactlyInAnyOrder(
                "init", "status", "chat", "serve", "gateway", "profile",
                "provider", "tool", "session");
    }

    @Test
    void helpAndInvalidArgumentsUseStableExitCodes() {
        var command = OryxOsCommand.commandLine(new StubLauncher());
        var output = new StringWriter();
        command.setOut(new PrintWriter(output));

        assertThat(command.execute("--help")).isZero();
        assertThat(output.toString()).contains("Java-native AI Agent OS");
        assertThat(command.execute("missing-command"))
                .isEqualTo(CommandLine.ExitCode.USAGE);
    }

    @Test
    void initIsIdempotentAndDoesNotStartRuntime() {
        var launcher = new StubLauncher();
        var command = OryxOsCommand.commandLine(launcher);

        assertThat(command.execute("init", temp.toString())).isZero();
        assertThat(command.execute("init", temp.toString())).isZero();
        assertThat(temp.resolve(".oryxos/agents/default/AGENT.md"))
                .isRegularFile();
        assertThat(launcher.calls).isZero();
    }

    @Test
    void machineOutputRedactsCredentials() {
        var text = CliOutput.redact(
                "{\"apiKey\":\"placeholder-value\","
                        + "\"authorization\":\"Bearer private-value\","
                        + "\"status\":\"ok\"}");
        assertThat(text).doesNotContain("placeholder-value");
        assertThat(text).doesNotContain("private-value");
        assertThat(text).contains("[REDACTED]");
        assertThatCode(() -> new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(text)).doesNotThrowAnyException();
    }

    private static final class StubLauncher implements RuntimeLauncher {
        int calls;
        @Override public int chat(Path workspace, String profileName,
                String userId, java.io.Reader input, java.io.Writer output) {
            calls++;
            return 0;
        }
        @Override public int serve(Path workspace) { calls++; return 0; }
        @Override public int gateway(Path workspace) { calls++; return 0; }
        @Override public List<Map<String, Object>> providers(Path workspace) {
            calls++; return List.of();
        }
        @Override public List<Map<String, Object>> tools(Path workspace) {
            calls++; return List.of();
        }
        @Override public List<Map<String, Object>> sessions(Path workspace) {
            calls++; return List.of();
        }
    }
}
