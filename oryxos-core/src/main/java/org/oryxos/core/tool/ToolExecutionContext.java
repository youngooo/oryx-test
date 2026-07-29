package org.oryxos.core.tool;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

public record ToolExecutionContext(
        String sessionId,
        String profileName,
        Path workspaceRoot,
        Instant deadline,
        String channel,
        String userId,
        Map<String, String> safeMetadata) {

    public ToolExecutionContext {
        workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        safeMetadata = Map.copyOf(safeMetadata == null ? Map.of() : safeMetadata);
    }
}
