package org.oryxos.core.model;

import java.nio.file.Path;
import java.util.List;

public record AgentDefinition(
        String name,
        Path agentFile,
        String body,
        Profile profile,
        Path skillsDirectory,
        Path scriptsDirectory,
        Path referenceFile,
        LoadStatus loadStatus,
        List<String> validationErrors) {

    public enum LoadStatus { VALID, INVALID }

    public AgentDefinition {
        name = Profile.requireText(name, "agent name");
        agentFile = agentFile == null ? null : agentFile.toAbsolutePath().normalize();
        body = body == null ? "" : body.strip();
        loadStatus = loadStatus == null ? LoadStatus.INVALID : loadStatus;
        validationErrors = List.copyOf(validationErrors == null ? List.of() : validationErrors);
        if (loadStatus == LoadStatus.VALID) {
            if (agentFile == null || body.isBlank() || profile == null
                    || !validationErrors.isEmpty()) {
                throw new IllegalArgumentException("Valid agent definition is incomplete");
            }
        } else if (validationErrors.isEmpty()) {
            throw new IllegalArgumentException("Invalid agent requires validation errors");
        }
    }
}
