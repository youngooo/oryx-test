package org.oryxos.core.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record Profile(
        String name,
        String provider,
        String model,
        Set<String> tools,
        Set<String> channels,
        String notifyTarget,
        List<ScheduleDefinition> schedules,
        int maxIterations,
        Double temperature) {

    private static final Pattern NAME = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$");

    public Profile {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid profile name");
        }
        provider = requireText(provider, "provider");
        model = normalize(model);
        tools = immutableOrderedSet(tools);
        channels = immutableOrderedSet(channels == null || channels.isEmpty()
                ? Set.of("cli") : channels);
        notifyTarget = normalize(notifyTarget);
        schedules = List.copyOf(schedules == null ? List.of() : schedules);
        if (maxIterations < 1 || maxIterations > 10) {
            throw new IllegalArgumentException("maxIterations must be between 1 and 10");
        }
        if (temperature != null && (!Double.isFinite(temperature)
                || temperature < 0 || temperature > 2)) {
            throw new IllegalArgumentException("temperature must be between 0 and 2");
        }
    }

    private static Set<String> immutableOrderedSet(Set<String> source) {
        var copy = new LinkedHashSet<String>();
        if (source != null) {
            source.forEach(value -> copy.add(requireText(value, "set value")));
        }
        return Collections.unmodifiableSet(copy);
    }

    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
