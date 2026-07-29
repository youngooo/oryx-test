package org.oryxos.tool.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.oryxos.core.model.ToolResult;
import org.oryxos.core.port.Sandbox;

public final class WebhookNotifyAdapter implements NotifyChannelAdapter {

    private final URI endpoint;
    private final HttpClient client;
    private final Sandbox sandbox;
    private final ObjectMapper mapper;

    public WebhookNotifyAdapter(URI endpoint, HttpClient client,
            Sandbox sandbox, ObjectMapper mapper) {
        this.endpoint = java.util.Objects.requireNonNull(endpoint);
        this.client = java.util.Objects.requireNonNull(client);
        this.sandbox = java.util.Objects.requireNonNull(sandbox);
        this.mapper = java.util.Objects.requireNonNull(mapper);
    }

    @Override public String channel() { return "webhook"; }

    @Override
    public ToolResult send(String target, String title, String message) {
        try {
            sandbox.enforce(new Sandbox.Action("HTTP", endpoint.toString()));
            var payload = mapper.createObjectNode()
                    .put("target", target)
                    .put("title", title == null ? "" : title)
                    .put("message", message);
            var request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            mapper.writeValueAsString(payload))).build();
            var response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return ToolResult.success("Notification sent to " + target);
            }
            return ToolResult.failure("Notification endpoint returned status "
                    + response.statusCode(), response.statusCode() >= 500);
        } catch (java.net.http.HttpTimeoutException timeout) {
            return ToolResult.failure("Notification timed out", true);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return ToolResult.failure("Notification interrupted", true);
        } catch (Exception failure) {
            return ToolResult.failure("Notification failed: "
                    + failure.getClass().getSimpleName(), false);
        }
    }
}
