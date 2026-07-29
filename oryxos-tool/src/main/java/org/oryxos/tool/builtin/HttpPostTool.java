package org.oryxos.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.oryxos.core.model.ToolResult;
import org.oryxos.core.port.Sandbox;
import org.oryxos.core.tool.OryxTool;
import org.oryxos.core.tool.ToolExecutionContext;

public final class HttpPostTool implements OryxTool {

    private final HttpClient client;
    private final Sandbox sandbox;
    private final ObjectNode schema;

    public HttpPostTool(HttpClient client, Sandbox sandbox, ObjectMapper mapper) {
        this.client = java.util.Objects.requireNonNull(client);
        this.sandbox = java.util.Objects.requireNonNull(sandbox);
        schema = FileToolSupport.schema(mapper);
        var properties = schema.putObject("properties");
        properties.putObject("url").put("type", "string");
        properties.putObject("headers").put("type", "object");
        properties.putObject("body").put("type", "string");
        properties.putObject("contentType").put("type", "string");
        properties.putObject("timeoutSeconds").put("type", "integer");
        schema.putArray("required").add("url");
    }

    @Override public String getName() { return "http_post"; }
    @Override public String getDescription() { return "POST to an allowlisted HTTP(S) URL"; }
    @Override public JsonNode getInputSchema() { return schema.deepCopy(); }

    @Override
    public ToolResult execute(JsonNode arguments, ToolExecutionContext context) {
        try {
            var uri = URI.create(arguments.path("url").asText());
            var timeout = arguments.path("timeoutSeconds").asInt(10);
            if (timeout < 1 || timeout > 30) {
                return ToolResult.failure("timeoutSeconds must be between 1 and 30", false);
            }
            var body = arguments.path("body").asText("");
            var contentType = arguments.path("contentType")
                    .asText("application/json; charset=utf-8");
            for (int redirect = 0; redirect <= HttpClientPolicy.MAX_REDIRECTS; redirect++) {
                sandbox.enforce(new Sandbox.Action("HTTP", uri.toString()));
                var builder = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(timeout))
                        .header("Content-Type", contentType)
                        .POST(HttpRequest.BodyPublishers.ofString(body));
                var headerFailure = HttpClientPolicy.addHeaders(
                        builder, arguments.path("headers"));
                if (headerFailure != null) {
                    return headerFailure;
                }
                var response = client.send(builder.build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 300 && response.statusCode() < 400) {
                    var location = response.headers().firstValue("location");
                    if (location.isEmpty()) {
                        return ToolResult.failure("Redirect response omitted Location", false);
                    }
                    if (redirect == HttpClientPolicy.MAX_REDIRECTS) {
                        return ToolResult.failure("Too many HTTP redirects", false);
                    }
                    uri = uri.resolve(location.get());
                    continue;
                }
                return HttpClientPolicy.response(response.statusCode(), response.body());
            }
            return ToolResult.failure("HTTP redirect handling failed", false);
        } catch (java.net.http.HttpTimeoutException timeout) {
            return ToolResult.failure("HTTP request timed out", true);
        } catch (Sandbox.DeniedException denied) {
            return ToolResult.failure(denied.getMessage(), false);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return ToolResult.failure("HTTP request interrupted", true);
        } catch (Exception failure) {
            return ToolResult.failure("HTTP request failed: "
                    + failure.getClass().getSimpleName(), false);
        }
    }
}
