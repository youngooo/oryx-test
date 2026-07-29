package org.oryxos.core.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.oryxos.core.model.AgentDefinition;
import org.oryxos.core.model.Profile;
import org.oryxos.core.model.ScheduleDefinition;
import org.yaml.snakeyaml.Yaml;

public final class AgentLoader {

    private final Set<String> providers;
    private final Set<String> tools;
    private final Set<String> channels;
    private final ZoneId defaultZone;

    public AgentLoader(Set<String> providers, Set<String> tools,
            Set<String> channels, ZoneId defaultZone) {
        this.providers = Set.copyOf(providers);
        this.tools = Set.copyOf(tools);
        this.channels = Set.copyOf(channels);
        this.defaultZone = defaultZone;
    }

    public List<AgentDefinition> load(Path workspace) {
        var agents = workspace.toAbsolutePath().normalize().resolve("agents");
        if (!Files.isDirectory(agents)) {
            return List.of();
        }
        try (var directories = Files.list(agents)) {
            return directories.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(this::loadOne)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to scan Agent directories", exception);
        }
    }

    private AgentDefinition loadOne(Path directory) {
        var name = directory.getFileName().toString();
        var agentFile = directory.resolve("AGENT.md");
        var errors = new ArrayList<String>();
        Profile profile = null;
        String body = "";
        try {
            if (!Files.isRegularFile(agentFile)) {
                errors.add("AGENT.md is required");
            } else {
                var document = parse(Files.readString(agentFile));
                body = document.body();
                if (body.isBlank()) {
                    errors.add("AGENT.md body is required");
                }
                profile = buildProfile(name, document.frontmatter(), errors);
            }
        } catch (Exception exception) {
            errors.add(safeMessage(exception));
        }
        var status = errors.isEmpty()
                ? AgentDefinition.LoadStatus.VALID : AgentDefinition.LoadStatus.INVALID;
        return new AgentDefinition(name, agentFile, body, profile,
                directoryIfPresent(directory.resolve("skills")),
                directoryIfPresent(directory.resolve("scripts")),
                fileIfPresent(directory.resolve("REFERENCE.md")),
                status, errors);
    }

    private Profile buildProfile(String name, Map<String, Object> yaml,
            List<String> errors) {
        var provider = text(yaml.get("provider"));
        if (provider == null) {
            errors.add("provider is required");
        } else if (!providers.contains(provider)) {
            errors.add("Unknown provider: " + provider);
        }
        var configuredTools = strings(yaml.get("tools"));
        configuredTools.stream().filter(tool -> !tools.contains(tool))
                .forEach(tool -> errors.add("Unknown tool: " + tool));
        var configuredChannels = strings(yaml.get("channels"));
        if (configuredChannels.isEmpty()) {
            configuredChannels.add("cli");
        }
        configuredChannels.stream().filter(channel -> !channels.contains(channel))
                .forEach(channel -> errors.add("Unknown channel: " + channel));
        var schedules = schedules(yaml.get("schedules"), errors);
        var maxIterations = integer(yaml.get("max_iterations"), 10);
        var temperature = decimal(yaml.get("temperature"));
        var notifyTarget = nestedText(yaml.get("notify"), "target");
        if (!errors.isEmpty()) {
            return null;
        }
        try {
            return new Profile(name, provider, text(yaml.get("model")),
                    configuredTools, configuredChannels, notifyTarget,
                    schedules, maxIterations, temperature);
        } catch (IllegalArgumentException exception) {
            errors.add(exception.getMessage());
            return null;
        }
    }

    private List<ScheduleDefinition> schedules(Object value, List<String> errors) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            errors.add("schedules must be a list");
            return List.of();
        }
        var result = new ArrayList<ScheduleDefinition>();
        var ids = new java.util.HashSet<String>();
        for (var item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                errors.add("schedule must be an object");
                continue;
            }
            try {
                var id = text(map.get("id"));
                if (!ids.add(id)) {
                    throw new IllegalArgumentException("Duplicate schedule id: " + id);
                }
                var zone = text(map.get("zone"));
                result.add(new ScheduleDefinition(id, text(map.get("cron")),
                        zone == null ? defaultZone : ZoneId.of(zone),
                        text(map.get("prompt")), booleanValue(map.get("enabled"), true)));
            } catch (Exception exception) {
                errors.add(safeMessage(exception));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Document parse(String content) {
        var normalized = content.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) {
            throw new IllegalArgumentException("AGENT.md frontmatter is required");
        }
        var end = normalized.indexOf("\n---\n", 4);
        if (end < 0) {
            throw new IllegalArgumentException("AGENT.md frontmatter is not closed");
        }
        var loaded = new Yaml().load(normalized.substring(4, end));
        var yaml = loaded instanceof Map<?, ?> map
                ? (Map<String, Object>) map : Map.<String, Object>of();
        return new Document(yaml, normalized.substring(end + 5).strip());
    }

    private LinkedHashSet<String> strings(Object value) {
        var result = new LinkedHashSet<String>();
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> {
                var text = text(item);
                if (text != null) {
                    result.add(text);
                }
            });
        }
        return result;
    }

    private String nestedText(Object value, String key) {
        return value instanceof Map<?, ?> map ? text(map.get(key)) : null;
    }

    private String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    private int integer(Object value, int fallback) {
        return value == null ? fallback : Integer.parseInt(value.toString());
    }

    private Double decimal(Object value) {
        return value == null ? null : Double.valueOf(value.toString());
    }

    private boolean booleanValue(Object value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(value.toString());
    }

    private Path directoryIfPresent(Path path) {
        return Files.isDirectory(path) ? path.toAbsolutePath().normalize() : null;
    }

    private Path fileIfPresent(Path path) {
        return Files.isRegularFile(path) ? path.toAbsolutePath().normalize() : null;
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private record Document(Map<String, Object> frontmatter, String body) {
    }
}
