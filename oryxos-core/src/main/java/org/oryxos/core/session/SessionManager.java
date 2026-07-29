package org.oryxos.core.session;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.oryxos.core.model.Message;
import org.oryxos.core.model.Session;
import org.oryxos.core.port.SessionStore;

public final class SessionManager {

    private final SessionStore store;
    private final SessionCoordinator coordinator;

    public SessionManager(SessionStore store) {
        this(store, new SessionCoordinator());
    }

    public SessionManager(SessionStore store, SessionCoordinator coordinator) {
        this.store = java.util.Objects.requireNonNull(store);
        this.coordinator = java.util.Objects.requireNonNull(coordinator);
    }

    public Session create(String profile, String channel, String user, Instant now) {
        return store.save(new Session(UUID.randomUUID().toString(), profile,
                channel, user, List.of(), Session.Status.ACTIVE,
                now, now, null, 0));
    }

    public Optional<Session> findActive(String profile, String channel, String user) {
        return store.findActive(profile, channel, user);
    }

    public Session require(String id) {
        return store.findById(id).orElseThrow(() ->
                new IllegalArgumentException("Unknown Session: " + id));
    }

    public Session append(String id, Message message) {
        return store.append(id, message);
    }

    public Session archive(String id, Instant at) {
        return coordinator.execute(id, () -> store.archive(id, at));
    }

    public List<Message> history(String id, int limit) {
        return store.findMessages(id, limit);
    }

    public <T> T serialized(String id, Supplier<T> action) {
        return coordinator.execute(id, action);
    }

    public int activeCoordinatorCount() {
        return coordinator.activeCount();
    }
}
