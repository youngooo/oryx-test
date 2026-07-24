package org.oryxos.cli;

import org.oryxos.core.ModuleDescriptor;

/**
 * Picocli command boundary.
 */
public final class OryxOsCliModule {

    public static final ModuleDescriptor DESCRIPTOR =
            new ModuleDescriptor("oryxos-cli", "Picocli command entry point");

    private OryxOsCliModule() {
    }
}

