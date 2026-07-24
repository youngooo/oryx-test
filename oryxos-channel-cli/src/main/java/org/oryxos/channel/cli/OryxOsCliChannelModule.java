package org.oryxos.channel.cli;

import org.oryxos.core.ModuleDescriptor;

/**
 * Interactive terminal channel boundary.
 */
public final class OryxOsCliChannelModule {

    public static final ModuleDescriptor DESCRIPTOR =
            new ModuleDescriptor("oryxos-channel-cli", "Interactive command-line channel");

    private OryxOsCliChannelModule() {
    }
}

