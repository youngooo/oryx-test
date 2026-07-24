package org.oryxos.provider;

import org.oryxos.core.ModuleDescriptor;

/**
 * Provider abstraction and explicit provider-name-to-model mapping boundary.
 */
public final class OryxOsProviderModule {

    public static final ModuleDescriptor DESCRIPTOR =
            new ModuleDescriptor("oryxos-provider", "LLM provider adapters and model mapping");

    private OryxOsProviderModule() {
    }
}

