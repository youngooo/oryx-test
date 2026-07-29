package org.oryxos.core.port;

import java.util.Optional;
import java.util.List;
import org.oryxos.core.model.Message;
import org.oryxos.core.model.Session;

public interface SessionStore {
    Session save(Session session);
    Optional<Session> findById(String sessionId);
    Optional<Session> findActive(String profileName, String channel, String userId);
    Session append(String sessionId, Message message);

    default Session archive(String sessionId, java.time.Instant at) {
        var session = findById(sessionId)
                .orElseThrow(() -> new java.util.NoSuchElementException(
                        "Session not found: " + sessionId));
        return save(session.archive(at));
    }

    default List<Message> findMessages(String sessionId, int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("message limit must be 1..100");
        }
        var messages = findById(sessionId)
                .orElseThrow(() -> new java.util.NoSuchElementException(
                        "Session not found: " + sessionId))
                .messages();
        return List.copyOf(messages.subList(
                Math.max(0, messages.size() - limit), messages.size()));
    }

    default List<Session> findAll(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("session limit must be 1..100");
        }
        return List.of();
    }
}
