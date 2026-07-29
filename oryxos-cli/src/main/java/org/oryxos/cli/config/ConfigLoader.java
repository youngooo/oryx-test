package org.oryxos.cli.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ConfigLoader {
    public Path workspace(Path selected) {
        var normalized = (selected == null ? Path.of(".") : selected)
                .toAbsolutePath().normalize();
        return normalized.getFileName() != null
                && ".oryxos".equals(normalized.getFileName().toString())
                ? normalized : normalized.resolve(".oryxos");
    }

    public boolean validWorkspace(Path selected) {
        var root = workspace(selected);
        return Files.isDirectory(root)
                && Files.isDirectory(root.resolve("agents"))
                && Files.isDirectory(root.resolve("memory"));
    }

    public List<String> agentNames(Path selected) {
        var directory = workspace(selected).resolve("agents");
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var paths = Files.list(directory)) {
            return paths.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted().toList();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Unable to list Agents", failure);
        }
    }
}
