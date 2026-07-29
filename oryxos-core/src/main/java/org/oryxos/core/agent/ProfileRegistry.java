package org.oryxos.core.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.oryxos.core.model.AgentDefinition;
import org.oryxos.core.model.Profile;

public final class ProfileRegistry {

    private volatile Map<String, Profile> snapshot = Map.of();
    private final java.util.function.Consumer<List<Profile>> reloadListener;

    public ProfileRegistry() {
        this(profiles -> { });
    }

    public ProfileRegistry(java.util.function.Consumer<List<Profile>> reloadListener) {
        this.reloadListener = java.util.Objects.requireNonNull(reloadListener);
    }

    public void reload(List<AgentDefinition> definitions) {
        var next = new LinkedHashMap<String, Profile>();
        definitions.stream()
                .filter(definition -> definition.loadStatus()
                        == AgentDefinition.LoadStatus.VALID)
                .forEach(definition -> {
                    if (next.putIfAbsent(definition.name(), definition.profile()) != null) {
                        throw new IllegalStateException("Duplicate Agent: " + definition.name());
                    }
                });
        snapshot = Map.copyOf(next);
        reloadListener.accept(list());
    }

    public Optional<Profile> find(String name) {
        return Optional.ofNullable(snapshot.get(name));
    }

    public List<Profile> list() {
        return snapshot.values().stream()
                .sorted(java.util.Comparator.comparing(Profile::name))
                .toList();
    }
}
