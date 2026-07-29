package org.oryxos.cli.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.Callable;
import org.oryxos.cli.config.ConfigLoader;
import org.oryxos.cli.output.CliOutput;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "profile", description = "Manage Agent directories")
public final class ProfileCommand implements Runnable {
    @Override public void run() {
    }

    @Command(name = "list", description = "List Agent-derived Profiles")
    public static final class ListCommand implements Callable<Integer> {
        private final ConfigLoader config;
        private final CliOutput output;
        @Option(names = "--workspace", defaultValue = ".") private Path workspace;
        @Option(names = "--json") private boolean json;

        public ListCommand(ConfigLoader config, CliOutput output) {
            this.config = config; this.output = output;
        }
        @Override public Integer call() {
            output.value(config.agentNames(workspace), json);
            return 0;
        }
    }

    @Command(name = "create", description = "Create an Agent directory")
    public static final class CreateCommand implements Callable<Integer> {
        private final ConfigLoader config;
        private final CliOutput output;
        @Parameters(index = "0") private String name;
        @Option(names = "--workspace", defaultValue = ".") private Path workspace;

        public CreateCommand(ConfigLoader config, CliOutput output) {
            this.config = config; this.output = output;
        }
        @Override public Integer call() {
            if (name == null
                    || !name.matches("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$")) {
                output.error("Invalid Agent name");
                return 2;
            }
            var directory = config.workspace(workspace).resolve("agents")
                    .resolve(name).normalize();
            var file = directory.resolve("AGENT.md");
            try {
                Files.createDirectories(directory);
                if (Files.exists(file)) {
                    output.error("Agent already exists: " + name);
                    return 3;
                }
                Files.writeString(file, """
                        ---
                        provider: deepseek
                        channels:
                          - cli
                          - api
                        max_iterations: 10
                        ---
                        # Agent

                        Describe this Agent's task and boundaries.
                        """);
                output.value(Map.of("name", name,
                        "agentFile", file.toString()), true);
                return 0;
            } catch (IOException failure) {
                output.error("Unable to create Agent");
                return 3;
            }
        }
    }

    @Command(name = "show", description = "Show an Agent definition")
    public static final class ShowCommand implements Callable<Integer> {
        private final ConfigLoader config;
        private final CliOutput output;
        @Parameters(index = "0") private String name;
        @Option(names = "--workspace", defaultValue = ".") private Path workspace;

        public ShowCommand(ConfigLoader config, CliOutput output) {
            this.config = config; this.output = output;
        }
        @Override public Integer call() {
            var file = config.workspace(workspace).resolve("agents")
                    .resolve(name).resolve("AGENT.md").normalize();
            if (!Files.isRegularFile(file)) {
                output.error("Agent not found: " + name);
                return 4;
            }
            try {
                output.value(CliOutput.redact(Files.readString(file)), false);
                return 0;
            } catch (IOException failure) {
                output.error("Unable to read Agent");
                return 3;
            }
        }
    }

    @Command(name = "delete", description = "Delete an Agent definition")
    public static final class DeleteCommand implements Callable<Integer> {
        private final ConfigLoader config;
        private final CliOutput output;
        @Parameters(index = "0") private String name;
        @Option(names = "--workspace", defaultValue = ".") private Path workspace;
        @Option(names = "--force") private boolean force;

        public DeleteCommand(ConfigLoader config, CliOutput output) {
            this.config = config; this.output = output;
        }
        @Override public Integer call() {
            if (!force) {
                output.error("Deletion requires --force confirmation");
                return 2;
            }
            var agents = config.workspace(workspace).resolve("agents")
                    .toAbsolutePath().normalize();
            var target = agents.resolve(name).normalize();
            if (target.equals(agents) || !target.startsWith(agents)
                    || !Files.isDirectory(target)) {
                output.error("Agent not found: " + name);
                return 4;
            }
            try (var paths = Files.walk(target)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
                output.value("Deleted Agent " + name
                        + "; Session and audit history were retained", false);
                return 0;
            } catch (IOException failure) {
                output.error("Unable to delete Agent");
                return 3;
            }
        }
    }
}
