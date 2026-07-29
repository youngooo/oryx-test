package org.oryxos.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.oryxos.core.model.ToolResult;
import org.oryxos.core.tool.OryxTool;
import org.oryxos.core.tool.ToolExecutionContext;

public final class WriteFileTool implements OryxTool {

    private final ObjectNode schema;

    public WriteFileTool(ObjectMapper mapper) {
        schema = FileToolSupport.schema(mapper);
        var properties = schema.putObject("properties");
        properties.putObject("path").put("type", "string");
        properties.putObject("content").put("type", "string");
        properties.putObject("createParents").put("type", "boolean");
        schema.putArray("required").add("path").add("content");
    }

    @Override public String getName() { return "write_file"; }
    @Override public String getDescription() { return "Write a UTF-8 file inside the workspace"; }
    @Override public JsonNode getInputSchema() { return schema.deepCopy(); }

    @Override
    public ToolResult execute(JsonNode arguments, ToolExecutionContext context) {
        try {
            var path = FileToolSupport.resolve(context.workspaceRoot(),
                    arguments.path("path").asText());
            var parent = path.getParent();
            if (arguments.path("createParents").asBoolean(false) && parent != null) {
                Files.createDirectories(parent);
            }
            if (parent == null || !Files.isDirectory(parent)) {
                return ToolResult.failure("Parent directory does not exist", false);
            }
            var content = arguments.path("content").asText();
            Files.writeString(path, content, StandardCharsets.UTF_8);
            return ToolResult.success("Wrote " + content.length() + " characters");
        } catch (Exception failure) {
            return ToolResult.failure("Unable to write file: "
                    + failure.getClass().getSimpleName(), false);
        }
    }
}
