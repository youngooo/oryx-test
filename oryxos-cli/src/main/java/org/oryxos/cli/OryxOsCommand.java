package org.oryxos.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import org.oryxos.channel.cli.CliChannel;
import org.oryxos.cli.command.ChatCommand;
import org.oryxos.cli.command.GatewayCommand;
import org.oryxos.cli.command.InitCommand;
import org.oryxos.cli.command.ProfileCommand;
import org.oryxos.cli.command.ProviderCommand;
import org.oryxos.cli.command.ServeCommand;
import org.oryxos.cli.command.SessionCommand;
import org.oryxos.cli.command.StatusCommand;
import org.oryxos.cli.command.ToolCommand;
import org.oryxos.cli.config.ConfigLoader;
import org.oryxos.cli.output.CliOutput;
import org.oryxos.cli.runtime.RuntimeLauncher;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "oryxos", mixinStandardHelpOptions = true,
        version = OryxOsCommand.VERSION,
        description = "Java-native AI Agent OS")
public class OryxOsCommand implements Runnable {
    public static final String VERSION = "OryxOS 0.1.0-SNAPSHOT";
    private final PrintWriter out;

    public OryxOsCommand() {
        this(new PrintWriter(System.out, true));
    }

    OryxOsCommand(PrintWriter out) {
        this.out = out;
    }

    public static void main(String[] args) {
        var code = commandLine(unavailableRuntime()).execute(args);
        if (code != 0) {
            System.exit(code);
        }
    }

    public static CommandLine commandLine(CliChannel channel) {
        var launcher = new RuntimeLauncher() {
            @Override public int chat(Path workspace, String profile,
                    String user, java.io.Reader input, java.io.Writer output) {
                return channel.chat(profile, user, input, output);
            }
            @Override public int serve(Path workspace) { return 5; }
            @Override public int gateway(Path workspace) { return 5; }
        };
        return commandLine(launcher);
    }

    public static CommandLine commandLine(RuntimeLauncher launcher) {
        var out = new PrintWriter(System.out, true);
        var err = new PrintWriter(System.err, true);
        var output = new CliOutput(out, err);
        var config = new ConfigLoader();
        var root = new CommandLine(new OryxOsCommand(out));
        root.setExecutionExceptionHandler((failure, command, parsed) -> {
            output.error(failure.getMessage() == null
                    ? "Runtime command failed" : failure.getMessage());
            return 5;
        });
        root.addSubcommand("init", new InitCommand(output));
        root.addSubcommand("status", new StatusCommand(config, output));
        root.addSubcommand("chat", new ChatCommand(launcher));
        root.addSubcommand("serve", new ServeCommand(launcher));
        root.addSubcommand("gateway", new GatewayCommand(launcher));

        var profile = new CommandLine(new ProfileCommand());
        profile.addSubcommand("list",
                new ProfileCommand.ListCommand(config, output));
        profile.addSubcommand("create",
                new ProfileCommand.CreateCommand(config, output));
        profile.addSubcommand("show",
                new ProfileCommand.ShowCommand(config, output));
        profile.addSubcommand("delete",
                new ProfileCommand.DeleteCommand(config, output));
        root.addSubcommand("profile", profile);

        var provider = new CommandLine(new ProviderCommand());
        provider.addSubcommand("list",
                new ProviderCommand.ListCommand(launcher, output));
        root.addSubcommand("provider", provider);
        var tool = new CommandLine(new ToolCommand());
        tool.addSubcommand("list",
                new ToolCommand.ListCommand(launcher, output));
        root.addSubcommand("tool", tool);
        var session = new CommandLine(new SessionCommand());
        session.addSubcommand("list",
                new SessionCommand.ListCommand(launcher, output));
        root.addSubcommand("session", session);
        return root;
    }

    private static RuntimeLauncher unavailableRuntime() {
        return new RuntimeLauncher() {
            @Override public int chat(Path workspace, String profile,
                    String user, java.io.Reader input, java.io.Writer output) {
                return 5;
            }
            @Override public int serve(Path workspace) { return 5; }
            @Override public int gateway(Path workspace) { return 5; }
        };
    }

    @Override public void run() {
        out.println(VERSION);
    }
}
