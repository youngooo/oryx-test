package org.oryxos.cli.command;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import org.oryxos.cli.runtime.RuntimeLauncher;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "serve", description = "Start the REST service")
public final class ServeCommand implements Callable<Integer> {
    private final RuntimeLauncher launcher;
    @Option(names = "--workspace", defaultValue = ".")
    private Path workspace;

    public ServeCommand(RuntimeLauncher launcher) {
        this.launcher = launcher;
    }

    @Override public Integer call() {
        return launcher.serve(workspace);
    }
}
