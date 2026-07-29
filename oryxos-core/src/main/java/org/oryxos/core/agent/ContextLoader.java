package org.oryxos.core.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import org.oryxos.core.model.AgentDefinition;

public final class ContextLoader {

    private static final java.util.List<String> BOOTSTRAP_FILES =
            java.util.List.of("AGENTS.md", "SOUL.md", "USER.md");
    private final Path workspace;

    public ContextLoader(Path workspace) {
        this.workspace = workspace.toAbsolutePath().normalize();
    }

    public String load(AgentDefinition definition, Instant now, ZoneId zone) {
        if (definition.loadStatus() != AgentDefinition.LoadStatus.VALID) {
            throw new IllegalArgumentException("Cannot load context for invalid Agent");
        }
        var sections = new ArrayList<String>();
        sections.add(definition.body());
        for (var file : BOOTSTRAP_FILES) {
            var path = workspace.resolve(file);
            if (Files.isRegularFile(path)) {
                try {
                    sections.add(Files.readString(path).strip());
                } catch (IOException exception) {
                    throw new IllegalStateException("Unable to read bootstrap " + file, exception);
                }
            }
        }
        sections.add("Current date and time: " + ZonedDateTime.ofInstant(now, zone));
        return String.join("\n\n", sections);
    }
}
