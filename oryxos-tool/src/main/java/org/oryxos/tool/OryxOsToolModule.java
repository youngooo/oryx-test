package org.oryxos.tool;

import org.oryxos.core.ModuleDescriptor;

/**
 * Built-in tools, MCP adapters, registry, sandbox and notification boundary.
 */
public final class OryxOsToolModule {

    public static final ModuleDescriptor DESCRIPTOR =
            new ModuleDescriptor("oryxos-tool", "Tool registry, adapters and sandbox");

    private OryxOsToolModule() {
    }
}

