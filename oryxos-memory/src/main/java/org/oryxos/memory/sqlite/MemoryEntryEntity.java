package org.oryxos.memory.sqlite;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "memory_entries")
public class MemoryEntryEntity {
    @Id
    private String id;
    @Column(nullable = false)
    private String scope;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    @Column(name = "created_at", nullable = false)
    private String createdAt;

    protected MemoryEntryEntity() { }

    public MemoryEntryEntity(
            String id, String scope, String content, String createdAt) {
        this.id = id;
        this.scope = scope;
        this.content = content;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getScope() { return scope; }
    public String getContent() { return content; }
    public String getCreatedAt() { return createdAt; }
}
