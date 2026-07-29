package org.oryxos.tool.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.oryxos.core.model.ToolResult;
import org.oryxos.core.tool.OryxTool;

public final class McpClientService {

    public interface ClientFactory {
        Client connect(McpServerConfiguration config);
    }

    public interface Client extends AutoCloseable {
        List<RemoteTool> listTools();
        ToolResult call(String name, JsonNode arguments);
        @Override default void close() { }
    }

    public record RemoteTool(String name, String description, JsonNode inputSchema) {
        public RemoteTool {
            if (name == null || name.isBlank() || inputSchema == null
                    || !inputSchema.isObject()) {
                throw new IllegalArgumentException("Invalid MCP Tool metadata");
            }
        }
    }

    private final Map<String, Client> clients;
    private final List<OryxTool> tools;

    public McpClientService(Collection<McpServerConfiguration> configs,
            ClientFactory factory) {
        var connected = new java.util.LinkedHashMap<String, Client>();
        var discovered = new ArrayList<OryxTool>();
        var names = new java.util.HashSet<String>();
        try {
            for (var config : configs) {
                var client = factory.connect(config);
                if (connected.putIfAbsent(config.name(), client) != null) {
                    throw new IllegalStateException("Duplicate MCP server: " + config.name());
                }
                for (var tool : client.listTools()) {
                    if (!names.add(tool.name())) {
                        throw new IllegalStateException("Duplicate MCP Tool: " + tool.name());
                    }
                    discovered.add(new McpToolAdapter(config.name(), tool, client));
                }
            }
        } catch (RuntimeException failure) {
            connected.values().forEach(Client::close);
            throw failure;
        }
        clients = Map.copyOf(connected);
        tools = List.copyOf(discovered);
    }

    public List<OryxTool> tools() {
        return tools;
    }

    public void close() {
        clients.values().forEach(Client::close);
    }
}
