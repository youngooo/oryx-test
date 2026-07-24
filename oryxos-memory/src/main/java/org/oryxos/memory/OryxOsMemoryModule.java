package org.oryxos.memory;

import org.oryxos.core.ModuleDescriptor;

/**
 * Unified session and long-term memory boundary.
 */
public final class OryxOsMemoryModule {

    public static final ModuleDescriptor DESCRIPTOR =
            new ModuleDescriptor("oryxos-memory", "Session and long-term memory services");

    private OryxOsMemoryModule() {
    }
}

