package org.oryxos.tool.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.oryxos.core.model.ToolResult;
import org.oryxos.core.tool.OryxTool;
import org.oryxos.core.tool.ToolExecutionContext;

public final class NotifyTool implements OryxTool {

    private final Map<String, NotifyChannelAdapter> adapters;
    private final String defaultChannel;
    private final ObjectNode schema;

    public NotifyTool(java.util.Collection<? extends NotifyChannelAdapter> adapters,
            String defaultChannel, ObjectMapper mapper) {
        var indexed = new java.util.LinkedHashMap<String, NotifyChannelAdapter>();
        adapters.forEach(adapter -> {
            if (indexed.putIfAbsent(adapter.channel(), adapter) != null) {
                throw new IllegalStateException("Duplicate notification channel: "
                        + adapter.channel());
            }
        });
        this.adapters = Map.copyOf(indexed);
        this.defaultChannel = defaultChannel;
        schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        var properties = schema.putObject("properties");
        properties.putObject("message").put("type", "string");
        properties.putObject("target").put("type", "string");
        properties.putObject("title").put("type", "string");
        properties.putObject("channel").put("type", "string");
        schema.putArray("required").add("message");
    }

    @Override public String getName() { return "notify"; }
    @Override public String getDescription() { return "Send a notification to a configured target"; }
    @Override public JsonNode getInputSchema() { return schema.deepCopy(); }

    @Override
    public ToolResult execute(JsonNode arguments, ToolExecutionContext context) {
        var target = arguments.path("target").asText(
                context.safeMetadata().getOrDefault("notifyTarget", ""));
        if (target.isBlank()) {
            return ToolResult.failure("Notification target is required", false);
        }
        var channel = arguments.path("channel").asText(defaultChannel);
        var adapter = adapters.get(channel);
        if (adapter == null) {
            return ToolResult.failure("Unknown notification channel: " + channel, false);
        }
        return adapter.send(target,
                arguments.has("title") ? arguments.path("title").asText() : null,
                arguments.path("message").asText());
    }
}
