package org.oryxos.tool.mcp;

import java.net.URI;
import java.util.List;
import java.util.Map;

public record McpServerConfiguration(
        String name,
        Transport transport,
        String command,
        List<String> args,
        URI url,
        Map<String, String> headers) {

    public enum Transport { STDIO, HTTP }

    public McpServerConfiguration {
        if (name == null || name.isBlank() || transport == null) {
            throw new IllegalArgumentException("MCP server name and transport are required");
        }
        args = List.copyOf(args == null ? List.of() : args);
        headers = Map.copyOf(headers == null ? Map.of() : headers);
        if (transport == Transport.STDIO && (command == null || command.isBlank())) {
            throw new IllegalArgumentException("stdio MCP server requires command");
        }
        if (transport == Transport.HTTP && url == null) {
            throw new IllegalArgumentException("HTTP MCP server requires url");
        }
    }
}
