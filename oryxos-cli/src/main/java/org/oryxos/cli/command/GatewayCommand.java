package org.oryxos.cli.command;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import org.oryxos.cli.runtime.RuntimeLauncher;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "gateway", description = "Start configured inbound channels")
public final class GatewayCommand implements Callable<Integer> {
    private final RuntimeLauncher launcher;
    @Option(names = "--workspace", defaultValue = ".")
    private Path workspace;

    public GatewayCommand(RuntimeLauncher launcher) {
        this.launcher = launcher;
    }

    @Override public Integer call() {
        return launcher.gateway(workspace);
    }
}
