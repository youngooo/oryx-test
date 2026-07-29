package org.oryxos.cli.command;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import org.oryxos.cli.output.CliOutput;
import org.oryxos.core.workspace.WorkspaceInitializer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "init", description = "Initialize an OryxOS workspace")
public final class InitCommand implements Callable<Integer> {
    private final CliOutput output;

    @Parameters(index = "0", defaultValue = ".")
    private Path target;

    public InitCommand(CliOutput output) {
        this.output = output;
    }

    @Override
    public Integer call() {
        var workspace = target.toAbsolutePath().normalize().resolve(".oryxos");
        try {
            new WorkspaceInitializer().initialize(workspace);
            output.value("Initialized " + workspace, false);
            return 0;
        } catch (RuntimeException failure) {
            output.error(failure.getMessage());
            return 3;
        }
    }
}
