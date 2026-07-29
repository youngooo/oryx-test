package org.oryxos.core.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record Session(
        String sessionId,
        String profileName,
        String channel,
        String userId,
        List<Message> messages,
        Status status,
        Instant createdAt,
        Instant lastActiveAt,
        Instant archivedAt,
        long version) {

    public enum Status { ACTIVE, ARCHIVED }

    public Session {
        sessionId = Profile.requireText(sessionId, "sessionId");
        profileName = Profile.requireText(profileName, "profileName");
        channel = Profile.requireText(channel, "channel");
        userId = Profile.requireText(userId, "userId");
        messages = List.copyOf(messages == null ? List.of() : messages);
        long expected = 1;
        for (var message : messages) {
            if (message.sequence() != expected++) {
                throw new IllegalArgumentException("Messages must be strictly ordered");
            }
        }
        if (status == null || createdAt == null || lastActiveAt == null) {
            throw new IllegalArgumentException("Session status and timestamps are required");
        }
        if (lastActiveAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("lastActiveAt precedes createdAt");
        }
        if ((status == Status.ARCHIVED) != (archivedAt != null)) {
            throw new IllegalArgumentException("Archive timestamp is inconsistent");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must be non-negative");
        }
    }

    public Session append(Message message) {
        if (status == Status.ARCHIVED) {
            throw new IllegalStateException("Archived session cannot accept messages");
        }
        if (message.sequence() != messages.size() + 1L) {
            throw new IllegalArgumentException("Message sequence is not next");
        }
        var updated = new ArrayList<>(messages);
        updated.add(message);
        return new Session(sessionId, profileName, channel, userId, updated, status,
                createdAt, message.createdAt(), null, version);
    }

    public Session archive(Instant at) {
        if (status == Status.ARCHIVED) {
            return this;
        }
        if (at == null || at.isBefore(createdAt)) {
            throw new IllegalArgumentException("Invalid archive timestamp");
        }
        return new Session(sessionId, profileName, channel, userId, messages,
                Status.ARCHIVED, createdAt, lastActiveAt, at, version);
    }
}
