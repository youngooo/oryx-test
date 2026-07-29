package org.oryxos.tool.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import org.oryxos.core.model.ToolDefinition;
import org.oryxos.core.model.ToolResult;
import org.oryxos.core.tool.OryxTool;
import org.oryxos.core.tool.ToolExecutionContext;
import org.oryxos.tool.registry.OriginAwareTool;

final class McpToolAdapter implements OryxTool, OriginAwareTool {

    private final String server;
    private final McpClientService.RemoteTool metadata;
    private final McpClientService.Client client;

    McpToolAdapter(String server, McpClientService.RemoteTool metadata,
            McpClientService.Client client) {
        this.server = server;
        this.metadata = metadata;
        this.client = client;
    }

    @Override public String getName() { return metadata.name(); }
    @Override public String getDescription() { return metadata.description(); }
    @Override public JsonNode getInputSchema() { return metadata.inputSchema().deepCopy(); }
    @Override public ToolDefinition.Origin origin() { return ToolDefinition.Origin.MCP; }
    @Override public String source() { return server; }

    @Override
    public ToolResult execute(JsonNode arguments, ToolExecutionContext context) {
        try {
            var result = client.call(getName(), arguments);
            return result == null
                    ? ToolResult.failure("MCP server returned no result", false)
                    : result;
        } catch (RuntimeException failure) {
            return ToolResult.failure("MCP invocation failed: "
                    + failure.getClass().getSimpleName(), true);
        }
    }
}
