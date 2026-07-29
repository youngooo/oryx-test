package org.oryxos.cli.command;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import org.oryxos.cli.output.CliOutput;
import org.oryxos.cli.runtime.RuntimeLauncher;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "provider", description = "Inspect Providers")
public final class ProviderCommand implements Runnable {
    @Override public void run() {
    }

    @Command(name = "list", description = "List Provider status")
    public static final class ListCommand implements Callable<Integer> {
        private final RuntimeLauncher launcher; private final CliOutput output;
        @Option(names = "--workspace", defaultValue = ".") private Path workspace;
        @Option(names = "--json") private boolean json;
        public ListCommand(RuntimeLauncher launcher, CliOutput output) {
            this.launcher = launcher; this.output = output;
        }
        @Override public Integer call() {
            output.value(launcher.providers(workspace), json);
            return 0;
        }
    }
}
