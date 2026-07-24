package org.oryxos.storage;

import org.oryxos.core.ModuleDescriptor;

/**
 * SQLite persistence boundary for sessions and audit data.
 */
public final class OryxOsStorageModule {

    public static final ModuleDescriptor DESCRIPTOR =
            new ModuleDescriptor("oryxos-storage", "SQLite session and audit persistence");

    private OryxOsStorageModule() {
    }
}

