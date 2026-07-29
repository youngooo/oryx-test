package org.oryxos.core.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.oryxos.core.model.ToolResult;

public interface OryxTool {
    String getName();
    String getDescription();
    JsonNode getInputSchema();
    ToolResult execute(JsonNode arguments, ToolExecutionContext context);
}
