package org.oryxos.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "sessions")
public class SessionEntity {

    @Id
    @Column(name = "session_id", nullable = false)
    private String sessionId;
    @Column(name = "profile_name", nullable = false)
    private String profileName;
    @Column(nullable = false)
    private String channel;
    @Column(name = "user_id", nullable = false)
    private String userId;
    @Column(name = "messages_json", nullable = false, columnDefinition = "TEXT")
    private String messagesJson;
    @Column(nullable = false)
    private String status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt;
    @Column(name = "archived_at")
    private Instant archivedAt;
    @Version
    @Column(nullable = false)
    private long version;

    protected SessionEntity() {
    }

    public SessionEntity(String sessionId, String profileName, String channel,
            String userId, String messagesJson, String status, Instant createdAt,
            Instant lastActiveAt, Instant archivedAt, long version) {
        this.sessionId = sessionId;
        this.profileName = profileName;
        this.channel = channel;
        this.userId = userId;
        this.messagesJson = messagesJson;
        this.status = status;
        this.createdAt = createdAt;
        this.lastActiveAt = lastActiveAt;
        this.archivedAt = archivedAt;
        this.version = version;
    }

    public String getSessionId() { return sessionId; }
    public String getProfileName() { return profileName; }
    public String getChannel() { return channel; }
    public String getUserId() { return userId; }
    public String getMessagesJson() { return messagesJson; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastActiveAt() { return lastActiveAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public long getVersion() { return version; }
}
