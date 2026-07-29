package org.oryxos.storage.adapter;

import java.util.NoSuchElementException;
import java.util.Optional;
import org.oryxos.core.model.Message;
import org.oryxos.core.model.Session;
import org.oryxos.core.port.SessionStore;
import org.oryxos.storage.entity.SessionEntity;
import org.oryxos.storage.repository.SessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaSessionStore implements SessionStore {

    private final SessionRepository repository;
    private final SessionJsonConverter converter;

    public JpaSessionStore(SessionRepository repository, SessionJsonConverter converter) {
        this.repository = repository;
        this.converter = converter;
    }

    @Override
    @Transactional
    public Session save(Session session) {
        return toDomain(repository.saveAndFlush(toEntity(session)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Session> findById(String sessionId) {
        return repository.findById(sessionId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Session> findActive(String profileName, String channel, String userId) {
        return repository
                .findFirstByProfileNameAndChannelAndUserIdAndStatusOrderByLastActiveAtDesc(
                        profileName, channel, userId, Session.Status.ACTIVE.name())
                .map(this::toDomain);
    }

    @Override
    @Transactional
    public Session append(String sessionId, Message message) {
        var current = repository.findById(sessionId)
                .map(this::toDomain)
                .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));
        return save(current.append(message));
    }

    @Override
    @Transactional
    public Session archive(String sessionId, java.time.Instant at) {
        var current = repository.findById(sessionId)
                .map(this::toDomain)
                .orElseThrow(() -> new NoSuchElementException(
                        "Session not found: " + sessionId));
        return current.status() == Session.Status.ARCHIVED
                ? current : save(current.archive(at));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<Session> findAll(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("session limit must be 1..100");
        }
        return repository.findAllByOrderByCreatedAtDesc(
                        org.springframework.data.domain.PageRequest.of(0, limit))
                .stream().map(this::toDomain).toList();
    }

    private SessionEntity toEntity(Session session) {
        return new SessionEntity(session.sessionId(), session.profileName(),
                session.channel(), session.userId(), converter.write(session.messages()),
                session.status().name(), session.createdAt(), session.lastActiveAt(),
                session.archivedAt(), session.version());
    }

    private Session toDomain(SessionEntity entity) {
        return new Session(entity.getSessionId(), entity.getProfileName(),
                entity.getChannel(), entity.getUserId(),
                converter.read(entity.getMessagesJson()),
                Session.Status.valueOf(entity.getStatus()), entity.getCreatedAt(),
                entity.getLastActiveAt(), entity.getArchivedAt(), entity.getVersion());
    }
}
