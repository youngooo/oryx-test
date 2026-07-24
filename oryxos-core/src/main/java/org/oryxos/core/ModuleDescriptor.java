package org.oryxos.core;

import java.util.Objects;

/**
 * Describes one OryxOS Maven module without coupling the core module to
 * framework-specific component models.
 *
 * @param name module artifact name
 * @param description concise module responsibility
 */
public record ModuleDescriptor(String name, String description) {

    public ModuleDescriptor {
        if (Objects.requireNonNull(name, "name must not be null").isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (Objects.requireNonNull(description, "description must not be null").isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
    }
}

