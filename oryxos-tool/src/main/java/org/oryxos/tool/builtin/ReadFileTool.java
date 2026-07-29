package org.oryxos.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.oryxos.core.model.ToolResult;
import org.oryxos.core.tool.OryxTool;
import org.oryxos.core.tool.ToolExecutionContext;

public final class ReadFileTool implements OryxTool {

    private static final int DEFAULT_MAX = 64 * 1024;
    private final ObjectNode schema;

    public ReadFileTool(ObjectMapper mapper) {
        schema = FileToolSupport.schema(mapper);
        var properties = schema.putObject("properties");
        properties.putObject("path").put("type", "string");
        properties.putObject("maxChars").put("type", "integer");
        schema.putArray("required").add("path");
    }

    @Override public String getName() { return "read_file"; }
    @Override public String getDescription() { return "Read a UTF-8 file inside the workspace"; }
    @Override public JsonNode getInputSchema() { return schema.deepCopy(); }

    @Override
    public ToolResult execute(JsonNode arguments, ToolExecutionContext context) {
        try {
            var max = arguments.path("maxChars").asInt(DEFAULT_MAX);
            if (max < 1 || max > 1024 * 1024) {
                return ToolResult.failure("maxChars must be between 1 and 1048576", false);
            }
            var path = FileToolSupport.resolve(context.workspaceRoot(),
                    arguments.path("path").asText());
            if (!Files.isRegularFile(path)) {
                return ToolResult.failure("File does not exist", false);
            }
            var content = Files.readString(path, StandardCharsets.UTF_8);
            return ToolResult.success(content.length() <= max ? content
                    : content.substring(0, max) + "\n[truncated]");
        } catch (Exception failure) {
            return ToolResult.failure("Unable to read file: "
                    + failure.getClass().getSimpleName(), false);
        }
    }
}
