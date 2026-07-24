package org.oryxos.core;

/**
 * Entry point for core contracts shared by every OryxOS module.
 */
public final class OryxOsCoreModule {

    public static final ModuleDescriptor DESCRIPTOR =
            new ModuleDescriptor("oryxos-core", "Core contracts and runtime orchestration");

    private OryxOsCoreModule() {
    }
}

