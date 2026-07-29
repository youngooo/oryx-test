package org.oryxos.tool.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.oryxos.core.model.ToolResult;
import org.oryxos.core.tool.OryxTool;
import org.oryxos.core.tool.ToolExecutionContext;

class ToolRegistryTest {

    @Test
    void rejectsDuplicateNamesAndReturnsOnlyProfileSubset() {
        assertThatThrownBy(() -> new ToolRegistry(List.of(
                tool("http_get"), tool("http_get"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");

        var registry = new ToolRegistry(List.of(
                tool("http_get"), tool("hidden")));
        assertThat(registry.availableTo(Set.of("http_get")))
                .extracting(OryxTool::getName)
                .containsExactly("http_get");
        assertThatThrownBy(() -> registry.availableTo(Set.of("missing")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown");
    }

    private OryxTool tool(String name) {
        var schema = new ObjectMapper().createObjectNode().put("type", "object");
        return new OryxTool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name; }
            @Override public JsonNode getInputSchema() { return schema; }
            @Override public ToolResult execute(
                    JsonNode arguments, ToolExecutionContext context) {
                return ToolResult.success("ok");
            }
        };
    }
}
