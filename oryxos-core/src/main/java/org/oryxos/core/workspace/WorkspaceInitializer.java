package org.oryxos.core.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WorkspaceInitializer {

    public void initialize(Path workspace) {
        var root = workspace.toAbsolutePath().normalize();
        try {
            ensureDirectory(root);
            ensureDirectory(root.resolve("agents"));
            ensureDirectory(root.resolve("agents/default"));
            ensureDirectory(root.resolve("memory"));
            ensureDirectory(root.resolve("logs"));
            createIfAbsent(root.resolve("agents/default/AGENT.md"), """
                    ---
                    provider: deepseek
                    model: deepseek-v4-flash
                    channels:
                      - cli
                    max_iterations: 10
                    ---
                    # Default Assistant

                    Be helpful, accurate, and concise.
                    """);
            createIfAbsent(root.resolve("memory/MEMORY.md"), "# OryxOS Memory\n");
            createIfAbsent(root.resolve("mcp_servers.yaml"), "servers: {}\n");
            createIfAbsent(root.resolve("AGENTS.md"), "# Workspace Rules\n");
            createIfAbsent(root.resolve("SOUL.md"), "# Assistant Identity\n");
            createIfAbsent(root.resolve("USER.md"), "# User Context\n");
            createIfAbsent(root.resolve("oryxos.db"), "");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize workspace " + root, exception);
        }
    }

    private void ensureDirectory(Path path) throws IOException {
        if (Files.exists(path) && !Files.isDirectory(path)) {
            throw new IllegalStateException("Expected directory but found conflicting path: " + path);
        }
        Files.createDirectories(path);
    }

    private void createIfAbsent(Path path, String content) throws IOException {
        if (Files.exists(path)) {
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("Expected file but found conflicting path: " + path);
            }
            return;
        }
        Files.writeString(path, content);
    }
}
