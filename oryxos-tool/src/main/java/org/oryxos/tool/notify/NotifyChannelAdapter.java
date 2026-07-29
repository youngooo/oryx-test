package org.oryxos.tool.notify;

import org.oryxos.core.model.ToolResult;

public interface NotifyChannelAdapter {
    String channel();
    ToolResult send(String target, String title, String message);
}
