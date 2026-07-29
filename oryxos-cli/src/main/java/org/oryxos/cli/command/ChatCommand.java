package org.oryxos.cli.command;

import java.io.Console;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import org.oryxos.channel.cli.CliChannel;
import org.oryxos.cli.runtime.RuntimeLauncher;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "chat",
        mixinStandardHelpOptions = true,
        description = "Start an interactive Agent conversation"
)
public final class ChatCommand implements Callable<Integer> {

    private final RuntimeLauncher launcher;
    private final Reader input;
    private final Writer output;

    @Option(names = "--profile", required = true,
            description = "Agent/Profile name")
    private String profileName;

    @Option(names = "--user", defaultValue = "cli-user",
            description = "Stable CLI user identifier")
    private String userId;

    @Option(names = "--workspace", defaultValue = ".",
            description = "Directory containing .oryxos")
    private Path workspace;

    public ChatCommand(CliChannel channel) {
        this(channel, terminalReader(), terminalWriter());
    }

    public ChatCommand(CliChannel channel, Reader input, Writer output) {
        this(new RuntimeLauncher() {
            @Override
            public int chat(Path workspace, String profileName, String userId,
                    Reader reader, Writer writer) {
                return channel.chat(profileName, userId, reader, writer);
            }
            @Override public int serve(Path workspace) { return 5; }
            @Override public int gateway(Path workspace) { return 5; }
        }, input, output);
    }

    public ChatCommand(RuntimeLauncher launcher) {
        this(launcher, terminalReader(), terminalWriter());
    }

    public ChatCommand(RuntimeLauncher launcher, Reader input, Writer output) {
        this.launcher = Objects.requireNonNull(launcher);
        this.input = Objects.requireNonNull(input);
        this.output = Objects.requireNonNull(output);
    }

    @Override
    public Integer call() {
        return launcher.chat(Objects.requireNonNull(workspace),
                profileName, userId, input, output);
    }

    /*
     * A real Windows console may use GBK/CP936 even though Java 21's default
     * charset is UTF-8. Console readers/writers honor that active code page.
     * Redirected streams remain UTF-8 so scripts and pipes stay deterministic.
     */
    static Reader terminalReader() {
        Console console = System.console();
        return console == null
                ? new InputStreamReader(System.in, StandardCharsets.UTF_8)
                : console.reader();
    }

    static Writer terminalWriter() {
        Console console = System.console();
        return console == null
                ? new OutputStreamWriter(System.out, StandardCharsets.UTF_8)
                : console.writer();
    }
}
