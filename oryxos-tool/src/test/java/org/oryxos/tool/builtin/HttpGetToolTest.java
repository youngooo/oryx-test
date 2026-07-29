package org.oryxos.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.oryxos.core.tool.ToolExecutionContext;
import org.oryxos.tool.sandbox.WhitelistSandbox;

class HttpGetToolTest {

    @TempDir Path workspace;
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/weather", exchange -> {
            var body = "{\"city\":\"Shanghai\",\"temperature\":29,\"condition\":\"rain\"}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/large", exchange -> {
            var body = "x".repeat(70 * 1024)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/weather");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(1200);
                var body = "late".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void fetchesAllowlistedWeatherAndBoundsLargeOutput() throws Exception {
        var tool = tool();
        var weather = tool.execute(mapper.readTree(
                "{\"url\":\"" + baseUrl + "/weather\"}"), context());
        var large = tool.execute(mapper.readTree(
                "{\"url\":\"" + baseUrl + "/large\"}"), context());

        assertThat(weather.success()).isTrue();
        assertThat(weather.content()).contains("\"temperature\":29");
        assertThat(large.success()).isTrue();
        assertThat(large.content()).hasSizeLessThanOrEqualTo(64 * 1024 + 12)
                .endsWith("[truncated]");
    }

    @Test
    void rejectsRedirectToUnapprovedHostAndInvalidInputs() throws Exception {
        var tool = tool();
        var redirect = tool.execute(mapper.readTree(
                "{\"url\":\"" + baseUrl + "/redirect\"}"), context());
        var timeout = tool.execute(mapper.readTree(
                "{\"url\":\"" + baseUrl + "/weather\",\"timeoutSeconds\":31}"),
                context());
        var malformed = tool.execute(mapper.readTree(
                "{\"url\":\"not a url\"}"), context());
        var slow = tool.execute(mapper.readTree(
                "{\"url\":\"" + baseUrl + "/slow\",\"timeoutSeconds\":1}"),
                context());

        assertThat(redirect.success()).isFalse();
        assertThat(redirect.error()).contains("allowlisted");
        assertThat(timeout.success()).isFalse();
        assertThat(malformed.success()).isFalse();
        assertThat(slow.success()).isFalse();
        assertThat(slow.retryable()).isTrue();
    }

    private HttpGetTool tool() {
        var sandbox = new WhitelistSandbox(Set.of("localhost"),
                Set.of(), Set.of(workspace));
        return new HttpGetTool(HttpGetTool.safeClient(), sandbox, mapper);
    }

    private ToolExecutionContext context() {
        return new ToolExecutionContext("session-1", "weather",
                workspace, Instant.now().plusSeconds(5),
                "cli", "user", Map.of());
    }
}
