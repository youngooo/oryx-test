package org.oryxos.core.model;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolDefinition(
        String name,
        String description,
        JsonNode inputSchema,
        Origin origin,
        String source) {

    public enum Origin { BUILT_IN, MCP, JAVA_PLUGIN }

    public ToolDefinition {
        name = Profile.requireText(name, "tool name");
        description = Profile.requireText(description, "tool description");
        if (inputSchema == null || !inputSchema.isObject()) {
            throw new IllegalArgumentException("inputSchema must be an object");
        }
        inputSchema = inputSchema.deepCopy();
        if (origin == null) {
            throw new IllegalArgumentException("origin is required");
        }
        source = Profile.requireText(source, "tool source");
    }

    @Override
    public JsonNode inputSchema() {
        return inputSchema.deepCopy();
    }
}
