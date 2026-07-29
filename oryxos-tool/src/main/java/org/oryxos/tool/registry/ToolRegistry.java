package org.oryxos.tool.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.oryxos.core.port.ToolCatalog;
import org.oryxos.core.model.ToolDefinition;
import org.oryxos.core.tool.OryxTool;

public final class ToolRegistry implements ToolCatalog {

    private final Map<String, OryxTool> tools;

    public ToolRegistry(Collection<? extends OryxTool> tools) {
        var registered = new LinkedHashMap<String, OryxTool>();
        for (var tool : tools == null ? List.<OryxTool>of() : tools) {
            if (tool == null || tool.getName() == null || tool.getName().isBlank()) {
                throw new IllegalArgumentException("Tool name is required");
            }
            if (registered.putIfAbsent(tool.getName(), tool) != null) {
                throw new IllegalStateException("Duplicate Tool name: " + tool.getName());
            }
        }
        this.tools = Map.copyOf(registered);
    }

    @Override
    public Optional<OryxTool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    @Override
    public List<OryxTool> availableTo(Set<String> names) {
        var selected = new ArrayList<OryxTool>();
        for (var name : names == null ? Set.<String>of() : names) {
            var tool = tools.get(name);
            if (tool == null) {
                throw new IllegalArgumentException("Unknown configured Tool: " + name);
            }
            selected.add(tool);
        }
        return List.copyOf(selected);
    }

    public List<OryxTool> all() {
        return tools.values().stream()
                .sorted(java.util.Comparator.comparing(OryxTool::getName))
                .toList();
    }

    @Override
    public List<ToolDefinition> definitions() {
        return all().stream().map(tool -> {
            var origin = tool instanceof OriginAwareTool aware
                    ? aware.origin() : ToolDefinition.Origin.BUILT_IN;
            var source = tool instanceof OriginAwareTool aware
                    ? aware.source() : "oryxos-tool";
            return new ToolDefinition(tool.getName(), tool.getDescription(),
                    tool.getInputSchema(), origin, source);
        }).toList();
    }
}
