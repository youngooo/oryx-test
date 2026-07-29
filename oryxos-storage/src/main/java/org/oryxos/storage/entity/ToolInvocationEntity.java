package org.oryxos.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tool_invocations")
public class ToolInvocationEntity {
    @Id private String id;
    @Column(name = "session_id", nullable = false) private String sessionId;
    @Column(name = "tool_name", nullable = false) private String toolName;
    @Column(name = "arguments_json", nullable = false) private String argumentsJson;
    @Column(nullable = false) private boolean success;
    @Column(name = "result_summary") private String resultSummary;
    @Column(name = "error_code") private String errorCode;
    @Column(name = "error_message") private String errorMessage;
    @Column(nullable = false) private boolean retryable;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "completed_at", nullable = false) private Instant completedAt;
    @Column(name = "duration_ms", nullable = false) private long durationMs;

    protected ToolInvocationEntity() {
    }

    public ToolInvocationEntity(String id, String sessionId, String toolName,
            String argumentsJson, boolean success, String resultSummary,
            String errorCode, String errorMessage, boolean retryable,
            Instant startedAt, Instant completedAt, long durationMs) {
        this.id = id;
        this.sessionId = sessionId;
        this.toolName = toolName;
        this.argumentsJson = argumentsJson;
        this.success = success;
        this.resultSummary = resultSummary;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.retryable = retryable;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.durationMs = durationMs;
    }

    public String getId() { return id; }
    public String getSessionId() { return sessionId; }
    public String getToolName() { return toolName; }
    public String getArgumentsJson() { return argumentsJson; }
    public boolean isSuccess() { return success; }
    public String getResultSummary() { return resultSummary; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public boolean isRetryable() { return retryable; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public long getDurationMs() { return durationMs; }
}
