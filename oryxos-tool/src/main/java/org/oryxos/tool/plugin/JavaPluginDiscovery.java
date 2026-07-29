package org.oryxos.tool.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.oryxos.core.tool.OryxTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.ListableBeanFactory;

/**
 * Discovers only Spring beans that actually declare at least one {@link Tool}
 * method; arbitrary beans are not adapted.
 */
public final class JavaPluginDiscovery {

    private final ListableBeanFactory beans;
    private final ObjectMapper mapper;

    public JavaPluginDiscovery(ListableBeanFactory beans, ObjectMapper mapper) {
        this.beans = beans;
        this.mapper = mapper;
    }

    public List<OryxTool> discover() {
        var pluginBeans = new ArrayList<Object>();
        for (var name : beans.getBeanDefinitionNames()) {
            var type = beans.getType(name, false);
            if (type != null && declaresTool(type)) {
                pluginBeans.add(beans.getBean(name));
            }
        }
        return JavaPluginToolAdapter.discover(mapper, pluginBeans);
    }

    private boolean declaresTool(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                return true;
            }
        }
        return false;
    }
}
