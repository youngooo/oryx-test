package org.oryxos.tool.notify;

import java.io.PrintWriter;
import org.oryxos.core.model.ToolResult;

public final class ConsoleNotificationAdapter implements NotifyChannelAdapter {

    private final PrintWriter writer;

    public ConsoleNotificationAdapter(PrintWriter writer) {
        this.writer = java.util.Objects.requireNonNull(writer);
    }

    @Override public String channel() { return "console"; }

    @Override
    public ToolResult send(String target, String title, String message) {
        writer.println("[" + target + "] "
                + (title == null ? "" : title + ": ") + message);
        writer.flush();
        return ToolResult.success("Notification sent to " + target);
    }
}
