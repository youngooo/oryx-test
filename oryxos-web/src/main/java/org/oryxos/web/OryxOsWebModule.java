package org.oryxos.web;

import org.oryxos.core.ModuleDescriptor;

/**
 * REST API boundary for OryxOS capabilities.
 */
public final class OryxOsWebModule {

    public static final ModuleDescriptor DESCRIPTOR =
            new ModuleDescriptor("oryxos-web", "REST API and HTTP integration");

    private OryxOsWebModule() {
    }
}

