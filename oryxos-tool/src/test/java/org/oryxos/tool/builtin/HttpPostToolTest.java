package org.oryxos.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.oryxos.core.tool.ToolExecutionContext;
import org.oryxos.tool.sandbox.WhitelistSandbox;

class HttpPostToolTest {

    @TempDir Path workspace;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> received = new AtomicReference<>();
    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/post", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8));
            var response = "accepted".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/post");
            exchange.sendResponseHeaders(307, -1);
            exchange.close();
        });
        server.createContext("/large", exchange -> {
            var response = "x".repeat(70 * 1024)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(1200);
                exchange.sendResponseHeaders(204, -1);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach void stop() { server.stop(0); }

    @Test
    void postsBodyAndRejectsSecretsAndRedirectHost() throws Exception {
        var tool = new HttpPostTool(HttpGetTool.safeClient(),
                new WhitelistSandbox(Set.of("localhost"), Set.of(), Set.of(workspace)),
                mapper);
        var success = tool.execute(mapper.readTree(
                "{\"url\":\"" + baseUrl + "/post\",\"body\":\"{\\\"ok\\\":true}\"}"),
                context());
        var secret = tool.execute(mapper.readTree(
                "{\"url\":\"" + baseUrl + "/post\",\"headers\":{\"Authorization\":\"x\"}}"),
                context());
        var redirect = tool.execute(mapper.readTree(
                "{\"url\":\"" + baseUrl + "/redirect\"}"), context());

        assertThat(success.success()).isTrue();
        assertThat(success.content()).isEqualTo("accepted");
        assertThat(received.get()).contains("\"ok\":true");
        assertThat(secret.success()).isFalse();
        assertThat(secret.error()).contains("Secret-bearing");
        assertThat(redirect.success()).isFalse();
        assertThat(redirect.error()).contains("allowlisted");
    }

    @Test
    void classifiesTimeoutAsRetryableAndBoundsResponse() throws Exception {
        var tool = new HttpPostTool(HttpGetTool.safeClient(),
                new WhitelistSandbox(Set.of("localhost"), Set.of(), Set.of(workspace)),
                mapper);
        var slow = tool.execute(mapper.readTree(
                "{\"url\":\"" + baseUrl + "/slow\",\"timeoutSeconds\":1}"), context());
        var large = tool.execute(mapper.readTree(
                "{\"url\":\"" + baseUrl + "/large\"}"), context());

        assertThat(slow.success()).isFalse();
        assertThat(slow.retryable()).isTrue();
        assertThat(large.success()).isTrue();
        assertThat(large.content()).endsWith("[truncated]")
                .hasSizeLessThanOrEqualTo(64 * 1024 + 12);
    }

    private ToolExecutionContext context() {
        return new ToolExecutionContext("session", "agent", workspace,
                Instant.now().plusSeconds(10), "test", "user", Map.of());
    }
}
