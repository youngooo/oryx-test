package org.oryxos.memory.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.Collectors;
import org.oryxos.core.memory.MemoryScope;
import org.oryxos.core.model.ToolResult;
import org.oryxos.core.port.MemoryService;
import org.oryxos.core.tool.OryxTool;
import org.oryxos.core.tool.ToolExecutionContext;

public final class MemoryTools {
    private MemoryTools() { }

    public static List<OryxTool> create(
            MemoryService memory, ObjectMapper mapper) {
        return List.of(new Save(memory, mapper), new Recall(memory, mapper));
    }

    private abstract static class Base implements OryxTool {
        final MemoryService memory;
        final JsonNode schema;
        Base(MemoryService memory, JsonNode schema) {
            this.memory = java.util.Objects.requireNonNull(memory);
            this.schema = schema;
        }
        @Override public JsonNode getInputSchema() { return schema; }
    }

    private static final class Save extends Base {
        Save(MemoryService memory, ObjectMapper mapper) {
            super(memory, saveSchema(mapper));
        }
        @Override public String getName() { return "save_memory"; }
        @Override public String getDescription() {
            return "Save a fact to shared long-term memory";
        }
        @Override
        public ToolResult execute(JsonNode arguments, ToolExecutionContext context) {
            try {
                var scope = MemoryScope.valueOf(arguments.path("scope")
                        .asText("ARCHIVAL").toUpperCase());
                memory.appendLongTerm(arguments.path("content").asText(), scope);
                return ToolResult.success("Memory saved in " + scope + " scope");
            } catch (RuntimeException failure) {
                return ToolResult.failure(failure.getMessage(), false);
            }
        }
    }

    private static final class Recall extends Base {
        Recall(MemoryService memory, ObjectMapper mapper) {
            super(memory, recallSchema(mapper));
        }
        @Override public String getName() { return "recall_memory"; }
        @Override public String getDescription() {
            return "Recall shared long-term memory by keyword";
        }
        @Override
        public ToolResult execute(JsonNode arguments, ToolExecutionContext context) {
            try {
                var items = memory.recallLongTerm(
                        arguments.path("keyword").asText(""),
                        arguments.path("maxChars").asInt(8192));
                return ToolResult.success(items.stream()
                        .map(item -> "[" + item.scope() + "] " + item.content())
                        .collect(Collectors.joining("\n")));
            } catch (RuntimeException failure) {
                return ToolResult.failure(failure.getMessage(), false);
            }
        }
    }

    private static JsonNode saveSchema(ObjectMapper mapper) {
        var root = mapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        var properties = root.putObject("properties");
        properties.putObject("content").put("type", "string")
                .put("minLength", 1).put("maxLength", 32768);
        properties.putObject("scope").put("type", "string")
                .putArray("enum").add("CORE").add("ARCHIVAL");
        root.putArray("required").add("content");
        return root;
    }

    private static JsonNode recallSchema(ObjectMapper mapper) {
        var root = mapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        var properties = root.putObject("properties");
        properties.putObject("keyword").put("type", "string");
        properties.putObject("maxChars").put("type", "integer")
                .put("minimum", 1);
        return root;
    }
}
