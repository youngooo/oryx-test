package org.oryxos.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.util.stream.Stream;
import org.oryxos.core.model.ToolResult;
import org.oryxos.core.tool.OryxTool;
import org.oryxos.core.tool.ToolExecutionContext;

public final class ListDirTool implements OryxTool {

    private final ObjectNode schema;

    public ListDirTool(ObjectMapper mapper) {
        schema = FileToolSupport.schema(mapper);
        var properties = schema.putObject("properties");
        properties.putObject("path").put("type", "string");
        properties.putObject("recursive").put("type", "boolean");
        properties.putObject("maxEntries").put("type", "integer");
        schema.putArray("required").add("path");
    }

    @Override public String getName() { return "list_dir"; }
    @Override public String getDescription() { return "List files inside a workspace directory"; }
    @Override public JsonNode getInputSchema() { return schema.deepCopy(); }

    @Override
    public ToolResult execute(JsonNode arguments, ToolExecutionContext context) {
        try {
            var path = FileToolSupport.resolve(context.workspaceRoot(),
                    arguments.path("path").asText());
            if (!Files.isDirectory(path)) {
                return ToolResult.failure("Directory does not exist", false);
            }
            var max = arguments.path("maxEntries").asInt(200);
            if (max < 1 || max > 1000) {
                return ToolResult.failure("maxEntries must be between 1 and 1000", false);
            }
            try (Stream<java.nio.file.Path> paths =
                         arguments.path("recursive").asBoolean(false)
                                 ? Files.walk(path) : Files.list(path)) {
                var entries = paths.filter(item -> !item.equals(path))
                        .sorted().limit(max + 1L).toList();
                var truncated = entries.size() > max;
                var result = entries.stream().limit(max)
                        .map(item -> path.relativize(item).toString()
                                + (Files.isDirectory(item) ? "/" : ""))
                        .collect(java.util.stream.Collectors.joining("\n"));
                return ToolResult.success(result
                        + (truncated ? "\n[truncated]" : ""));
            }
        } catch (Exception failure) {
            return ToolResult.failure("Unable to list directory: "
                    + failure.getClass().getSimpleName(), false);
        }
    }
}
