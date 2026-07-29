package org.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.oryxos.core.workspace.WorkspaceInitializer;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;

class PackagedRuntimeAcceptanceTest {
    @TempDir Path temp;

    @Test
    void restAndCliSeeTheSamePersistedSessionAfterRestart() throws Exception {
        var workspace = temp.resolve(".oryxos");
        new WorkspaceInitializer().initialize(workspace);
        var application = new SpringApplication(OryxOsApplication.class);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        var args = new String[] {
                "--server.port=0",
                "--oryxos.workspace=" + slash(workspace),
                "--spring.datasource.url=jdbc:sqlite:"
                        + slash(workspace.resolve("oryxos.db")),
                "--oryxos.scheduler.enabled=false"
        };
        String sessionId;
        try (var context = application.run(args)) {
            var port = context.getEnvironment().getRequiredProperty(
                    "local.server.port", Integer.class);
            var request = HttpRequest.newBuilder(URI.create(
                            "http://127.0.0.1:" + port + "/api/v1/sessions"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""
                            {"profileName":"default","channel":"api",
                             "userId":"acceptance"}
                            """, StandardCharsets.UTF_8))
                    .build();
            var response = HttpClient.newHttpClient().send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            var health = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                                    + port + "/api/v1/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            var openApi = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                                    + port + "/v3/api-docs")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertThat(response.statusCode()).isEqualTo(201);
            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(health.body()).contains("\"code\":\"OK\"",
                    "\"database\":\"UP\"");
            assertThat(openApi.statusCode()).isEqualTo(200);
            assertThat(openApi.body().split("\"operationId\"", -1).length - 1)
                    .isEqualTo(10);
            var marker = "\"sessionId\":\"";
            var start = response.body().indexOf(marker);
            assertThat(start).isGreaterThanOrEqualTo(0);
            var valueStart = start + marker.length();
            sessionId = response.body().substring(valueStart,
                    response.body().indexOf('"', valueStart));
        }

        var rows = new SpringRuntimeLauncher().sessions(temp);
        assertThat(rows).anySatisfy(row ->
                assertThat(row.get("sessionId")).isEqualTo(sessionId));
    }

    @Test
    void packagedJarSharesRestAndCliStateWhenArtifactIsSupplied()
            throws Exception {
        var configured = System.getProperty("oryxos.packagedJar");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                configured != null && !configured.isBlank(),
                "Run after package with -Doryxos.packagedJar=<jar>");
        var jar = Path.of(configured).toAbsolutePath().normalize();
        assertThat(jar).isRegularFile();
        var workspace = temp.resolve("packaged");
        var init = process(jar, "init", workspace.toString());
        assertThat(init.exitValue()).isZero();

        var port = freePort();
        var log = temp.resolve("packaged-service.log");
        var command = new ProcessBuilder(javaBinary(), "-jar", jar.toString(),
                "serve", "--workspace", workspace.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile());
        command.environment().put("ORYXOS_PORT", Integer.toString(port));
        var service = command.start();
        try {
            waitForHealth(port);
            var response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                                    + port + "/api/v1/sessions"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("""
                                    {"profileName":"default","channel":"api",
                                     "userId":"packaged"}
                                    """))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(201);
            var marker = "\"sessionId\":\"";
            var start = response.body().indexOf(marker) + marker.length();
            var sessionId = response.body().substring(start,
                    response.body().indexOf('"', start));

            service.destroy();
            assertThat(service.waitFor(10,
                    java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var list = process(jar, "session", "list", "--workspace",
                    workspace.toString(), "--json");
            assertThat(list.exitValue()).isZero();
            assertThat(list.inputReader(StandardCharsets.UTF_8)
                    .lines().collect(java.util.stream.Collectors.joining("\n")))
                    .contains(sessionId);
        } finally {
            if (service.isAlive()) {
                service.destroyForcibly();
            }
        }
    }

    private Process process(Path jar, String... arguments) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add(javaBinary());
        command.add("-jar");
        command.add(jar.toString());
        command.addAll(java.util.List.of(arguments));
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        assertThat(process.waitFor(20,
                java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        return process;
    }

    private void waitForHealth(int port) throws Exception {
        var client = HttpClient.newHttpClient();
        var uri = URI.create("http://127.0.0.1:" + port + "/api/v1/health");
        var deadline = System.nanoTime()
                + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            try {
                if (client.send(HttpRequest.newBuilder(uri).GET().build(),
                        HttpResponse.BodyHandlers.discarding())
                        .statusCode() == 200) {
                    return;
                }
            } catch (java.io.IOException ignored) {
                // Service is still starting.
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Packaged service did not become healthy");
    }

    private int freePort() throws Exception {
        try (var socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private String javaBinary() {
        return Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows")
                        ? "java.exe" : "java").toString();
    }

    private String slash(Path value) {
        return value.toAbsolutePath().normalize().toString()
                .replace('\\', '/');
    }
}
