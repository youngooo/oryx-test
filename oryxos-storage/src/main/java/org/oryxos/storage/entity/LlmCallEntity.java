package org.oryxos.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "llm_calls")
public class LlmCallEntity {
    @Id private String id;
    @Column(name = "session_id", nullable = false) private String sessionId;
    @Column(nullable = false) private String provider;
    @Column(nullable = false) private String model;
    @Column(nullable = false) private int iteration;
    @Column(name = "prompt_tokens") private Long promptTokens;
    @Column(name = "completion_tokens") private Long completionTokens;
    @Column(nullable = false) private boolean success;
    @Column(name = "finish_reason") private String finishReason;
    @Column(name = "error_code") private String errorCode;
    @Column(name = "error_message") private String errorMessage;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "completed_at", nullable = false) private Instant completedAt;
    @Column(name = "duration_ms", nullable = false) private long durationMs;

    protected LlmCallEntity() {
    }

    public LlmCallEntity(String id, String sessionId, String provider, String model,
            int iteration, Long promptTokens, Long completionTokens, boolean success,
            String finishReason, String errorCode, String errorMessage,
            Instant startedAt, Instant completedAt, long durationMs) {
        this.id = id;
        this.sessionId = sessionId;
        this.provider = provider;
        this.model = model;
        this.iteration = iteration;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.success = success;
        this.finishReason = finishReason;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.durationMs = durationMs;
    }

    public String getId() { return id; }
    public String getSessionId() { return sessionId; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public int getIteration() { return iteration; }
    public Long getPromptTokens() { return promptTokens; }
    public Long getCompletionTokens() { return completionTokens; }
    public boolean isSuccess() { return success; }
    public String getFinishReason() { return finishReason; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public long getDurationMs() { return durationMs; }
}
