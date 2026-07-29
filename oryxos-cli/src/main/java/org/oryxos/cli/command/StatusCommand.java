package org.oryxos.cli.command;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;
import org.oryxos.cli.config.ConfigLoader;
import org.oryxos.cli.output.CliOutput;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "status", description = "Show local OryxOS status")
public final class StatusCommand implements Callable<Integer> {
    private final ConfigLoader config;
    private final CliOutput output;

    @Option(names = "--workspace", defaultValue = ".")
    private Path workspace;
    @Option(names = "--json")
    private boolean json;

    public StatusCommand(ConfigLoader config, CliOutput output) {
        this.config = config;
        this.output = output;
    }

    @Override
    public Integer call() {
        var valid = config.validWorkspace(workspace);
        output.value(Map.of("workspace", config.workspace(workspace).toString(),
                "valid", valid, "agents", config.agentNames(workspace)), json);
        return valid ? 0 : 3;
    }
}
