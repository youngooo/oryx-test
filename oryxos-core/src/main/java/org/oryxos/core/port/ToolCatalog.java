package org.oryxos.core.port;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.oryxos.core.tool.OryxTool;
import org.oryxos.core.model.ToolDefinition;

public interface ToolCatalog {
    Optional<OryxTool> find(String name);
    List<OryxTool> availableTo(Set<String> names);

    default List<ToolDefinition> definitions() {
        return List.of();
    }
}
