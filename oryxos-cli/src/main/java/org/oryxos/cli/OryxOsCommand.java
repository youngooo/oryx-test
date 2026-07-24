package org.oryxos.cli;

import java.io.PrintWriter;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Root command. Feature subcommands are added incrementally with the runtime.
 */
@Command(
        name = "oryxos",
        mixinStandardHelpOptions = true,
        version = OryxOsCommand.VERSION,
        description = "Java-native AI Agent OS"
)
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
        int exitCode = new CommandLine(new OryxOsCommand()).execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    @Override
    public void run() {
        out.println(VERSION);
    }
}
