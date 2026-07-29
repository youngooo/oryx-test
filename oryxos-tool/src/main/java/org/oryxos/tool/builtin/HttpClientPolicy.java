package org.oryxos.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpRequest;
import java.util.Locale;
import java.util.Set;
import org.oryxos.core.model.ToolResult;

final class HttpClientPolicy {

    static final int MAX_REDIRECTS = 3;
    static final int MAX_RESULT_CHARS = 64 * 1024;
    private static final Set<String> FORBIDDEN_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie");

    private HttpClientPolicy() {
    }

    static ToolResult addHeaders(HttpRequest.Builder builder, JsonNode headers) {
        if (!headers.isMissingNode() && !headers.isObject()) {
            return ToolResult.failure("HTTP headers must be an object", false);
        }
        if (headers.isObject()) {
            var entries = headers.fields();
            while (entries.hasNext()) {
                var entry = entries.next();
                if (FORBIDDEN_HEADERS.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                    return ToolResult.failure("Secret-bearing headers are not accepted", false);
                }
                if (!entry.getValue().isTextual()) {
                    return ToolResult.failure("HTTP header values must be strings", false);
                }
                builder.header(entry.getKey(), entry.getValue().asText());
            }
        }
        return null;
    }

    static ToolResult response(int status, String body) {
        if (status < 200 || status >= 300) {
            return ToolResult.failure("HTTP request failed with status " + status,
                    status >= 500);
        }
        var safe = body == null ? "" : body;
        if (safe.length() > MAX_RESULT_CHARS) {
            safe = safe.substring(0, MAX_RESULT_CHARS) + "\n[truncated]";
        }
        return ToolResult.success(safe.isBlank() ? "(empty response)" : safe);
    }
}
