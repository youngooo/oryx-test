package org.oryxos.tool.registry;

import org.oryxos.core.model.ToolDefinition;

public interface OriginAwareTool {
    ToolDefinition.Origin origin();
    String source();
}
