package org.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.oryxos.cli.OryxOsCommand;
import org.oryxos.core.workspace.WorkspaceInitializer;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;

class CliBootIntegrationTest {
    @TempDir Path temp;

    @Test
    void lightHelpCommandStartsWithoutSpringWithinFiveHundredMilliseconds() {
        var started = System.nanoTime();
        var exit = OryxOsCommand.commandLine(
                new SpringRuntimeLauncher()).execute("--help");
        var elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertThat(exit).isZero();
        assertThat(elapsedMs).isLessThan(500);
    }

    @Test
    void initRemainsAContextFreeBootCommand() {
        var exit = OryxOsCommand.commandLine(
                new SpringRuntimeLauncher()).execute(
                        "init", temp.toString());

        assertThat(exit).isZero();
        assertThat(temp.resolve(".oryxos/oryxos.db")).isRegularFile();
    }

    @Test
    void warmedServiceReachesHealthWithinFourSeconds() throws Exception {
        var workspace = temp.resolve("timed/.oryxos");
        new WorkspaceInitializer().initialize(workspace);
        var args = new String[] {
                "--server.port=0",
                "--oryxos.workspace=" + slash(workspace),
                "--spring.datasource.url=jdbc:sqlite:"
                        + slash(workspace.resolve("oryxos.db")),
                "--oryxos.scheduler.enabled=false",
                "--logging.level.root=OFF"
        };
        try (var ignored = application().run(args)) {
            // Warm class loading so this controlled threshold measures service
            // startup rather than one-time JVM linkage.
        }

        var started = System.nanoTime();
        try (var context = application().run(args)) {
            var port = context.getEnvironment().getRequiredProperty(
                    "local.server.port", Integer.class);
            var response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                                    + port + "/api/v1/health")).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat((System.nanoTime() - started) / 1_000_000)
                    .isLessThanOrEqualTo(4_000);
        }
    }

    private SpringApplication application() {
        var application = new SpringApplication(OryxOsApplication.class);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        return application;
    }

    private String slash(Path value) {
        return value.toAbsolutePath().normalize().toString()
                .replace('\\', '/');
    }
}
