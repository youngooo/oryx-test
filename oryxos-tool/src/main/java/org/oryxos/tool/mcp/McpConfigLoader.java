package org.oryxos.tool.mcp;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

public final class McpConfigLoader {

    @SuppressWarnings("unchecked")
    public List<McpServerConfiguration> load(Path file) {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            var loaded = new Yaml().load(Files.readString(file));
            if (!(loaded instanceof Map<?, ?> root)) {
                return List.of();
            }
            var rawServers = root.get("servers");
            if (!(rawServers instanceof Map<?, ?> servers)) {
                return List.of();
            }
            var result = new ArrayList<McpServerConfiguration>();
            for (var entry : servers.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> value)) {
                    throw new IllegalArgumentException("MCP server must be an object");
                }
                var type = text(value.get("transport"), "stdio").toUpperCase();
                var args = value.get("args") instanceof List<?> list
                        ? list.stream().map(Object::toString).toList() : List.<String>of();
                var headers = value.get("headers") instanceof Map<?, ?> map
                        ? map.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                                item -> item.getKey().toString(),
                                item -> item.getValue().toString()))
                        : Map.<String, String>of();
                result.add(new McpServerConfiguration(entry.getKey().toString(),
                        McpServerConfiguration.Transport.valueOf(type),
                        text(value.get("command"), null), args,
                        value.get("url") == null ? null
                                : URI.create(value.get("url").toString()),
                        headers));
            }
            return List.copyOf(result);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to read MCP configuration", failure);
        }
    }

    private String text(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }
}
