package org.oryxos.tool.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.oryxos.core.model.ToolDefinition;
import org.oryxos.core.model.ToolResult;
import org.oryxos.core.tool.OryxTool;
import org.oryxos.core.tool.ToolExecutionContext;
import org.oryxos.tool.registry.OriginAwareTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public final class JavaPluginToolAdapter implements OryxTool, OriginAwareTool {

    private final Object bean;
    private final Method method;
    private final ObjectMapper mapper;
    private final String name;
    private final String description;
    private final ObjectNode schema;

    private JavaPluginToolAdapter(Object bean, Method method,
            Tool annotation, ObjectMapper mapper) {
        this.bean = bean;
        this.method = method;
        this.mapper = mapper;
        name = annotation.name().isBlank() ? method.getName() : annotation.name();
        description = annotation.description().isBlank()
                ? "Java plugin Tool " + name : annotation.description();
        schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        var properties = schema.putObject("properties");
        var required = schema.putArray("required");
        for (var parameter : method.getParameters()) {
            var property = properties.putObject(parameter.getName());
            property.put("type", jsonType(parameter.getType()));
            var metadata = parameter.getAnnotation(ToolParam.class);
            if (metadata != null && !metadata.description().isBlank()) {
                property.put("description", metadata.description());
            }
            if (metadata == null || metadata.required()) {
                required.add(parameter.getName());
            }
        }
        method.trySetAccessible();
    }

    public static List<OryxTool> discover(ObjectMapper mapper,
            Collection<?> pluginBeans) {
        var result = new ArrayList<OryxTool>();
        for (var bean : pluginBeans == null ? List.of() : pluginBeans) {
            for (var method : bean.getClass().getDeclaredMethods()) {
                var annotation = method.getAnnotation(Tool.class);
                if (annotation != null) {
                    result.add(new JavaPluginToolAdapter(bean, method, annotation, mapper));
                }
            }
        }
        return List.copyOf(result);
    }

    @Override public String getName() { return name; }
    @Override public String getDescription() { return description; }
    @Override public JsonNode getInputSchema() { return schema.deepCopy(); }
    @Override public ToolDefinition.Origin origin() {
        return ToolDefinition.Origin.JAVA_PLUGIN;
    }
    @Override public String source() { return bean.getClass().getName(); }

    @Override
    public ToolResult execute(JsonNode arguments, ToolExecutionContext context) {
        try {
            var parameters = method.getParameters();
            var values = new Object[parameters.length];
            for (int index = 0; index < parameters.length; index++) {
                var value = arguments.get(parameters[index].getName());
                values[index] = mapper.convertValue(value, parameters[index].getType());
            }
            var result = method.invoke(bean, values);
            if (result instanceof ToolResult toolResult) {
                return toolResult;
            }
            return ToolResult.success(result == null ? "(no result)"
                    : mapper.writeValueAsString(result));
        } catch (java.lang.reflect.InvocationTargetException failure) {
            return ToolResult.failure("Java plugin failed: "
                    + failure.getTargetException().getClass().getSimpleName(), false);
        } catch (Exception failure) {
            return ToolResult.failure("Java plugin failed: "
                    + failure.getClass().getSimpleName(), false);
        }
    }

    private static String jsonType(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) return "boolean";
        if (type == byte.class || type == short.class || type == int.class
                || type == long.class || Number.class.isAssignableFrom(type)) {
            return "number";
        }
        if (type.isArray() || Collection.class.isAssignableFrom(type)) return "array";
        if (type == String.class || type.isEnum() || type == char.class
                || type == Character.class) return "string";
        return "object";
    }
}
