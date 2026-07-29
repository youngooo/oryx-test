package org.oryxos.web.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.oryxos.core.model.LlmCallRecord;
import org.oryxos.core.model.Message;
import org.oryxos.core.model.Session;
import org.oryxos.core.model.ToolInvocationRecord;
import org.oryxos.core.port.InvocationAuditStore;

public final class SessionDtos {
    private static final ObjectMapper JSON = new ObjectMapper();

    private SessionDtos() {
    }

    public record CreateSessionRequest(
            @NotBlank @Size(max = 64) String profileName,
            @NotBlank @Size(max = 32) String channel,
            @NotBlank @Size(max = 128) String userId) {
    }

    public record MessageRequest(
            @NotBlank @Size(max = 32 * 1024) String content) {
    }

    public record PageMetadata(
            @Min(0) int offset,
            @Min(1) @Max(100) int limit,
            long total) {
    }

    public record MessageView(
            String messageId,
            long sequence,
            Message.Role role,
            String content,
            String toolCallId,
            String toolName,
            Instant createdAt) {
        static MessageView from(Message value) {
            return new MessageView(value.messageId(), value.sequence(),
                    value.role(), value.content(), value.toolCallId(),
                    value.toolName(), value.createdAt());
        }
    }

    public record SessionView(
            String sessionId,
            String profileName,
            String channel,
            String userId,
            List<MessageView> messages,
            Session.Status status,
            Instant createdAt,
            Instant lastActiveAt,
            Instant archivedAt) {
        public static SessionView from(Session value) {
            return from(value, value.messages());
        }

        public static SessionView from(Session value, List<Message> messages) {
            return new SessionView(value.sessionId(), value.profileName(),
                    value.channel(), value.userId(),
                    messages.stream().map(MessageView::from).toList(),
                    value.status(), value.createdAt(), value.lastActiveAt(),
                    value.archivedAt());
        }
    }

    public record ToolInvocationView(
            String id,
            String sessionId,
            String toolName,
            JsonNode arguments,
            boolean success,
            String resultSummary,
            String errorCode,
            String errorMessage,
            boolean retryable,
            Instant startedAt,
            Instant completedAt,
            long durationMs) {
        static ToolInvocationView from(ToolInvocationRecord value) {
            return new ToolInvocationView(value.id(), value.sessionId(),
                    value.toolName(), json(value.argumentsJson()), value.success(),
                    value.resultSummary(), value.errorCode(), value.errorMessage(),
                    value.retryable(), value.startedAt(), value.completedAt(),
                    value.durationMs());
        }
    }

    public record SessionDetail(
            String sessionId,
            String profileName,
            String channel,
            String userId,
            List<MessageView> messages,
            Session.Status status,
            Instant createdAt,
            Instant lastActiveAt,
            Instant archivedAt,
            List<LlmCallRecord> llmCalls,
            List<ToolInvocationView> toolInvocations,
            PageMetadata llmCallsPage,
            PageMetadata toolInvocationsPage) {
        public static SessionDetail from(Session value, List<Message> messages,
                InvocationAuditStore.Page<LlmCallRecord> llm,
                InvocationAuditStore.Page<ToolInvocationRecord> tools) {
            return new SessionDetail(value.sessionId(), value.profileName(),
                    value.channel(), value.userId(),
                    messages.stream().map(MessageView::from).toList(),
                    value.status(), value.createdAt(), value.lastActiveAt(),
                    value.archivedAt(), llm.items(),
                    tools.items().stream().map(ToolInvocationView::from).toList(),
                    new PageMetadata(llm.offset(), llm.limit(), llm.total()),
                    new PageMetadata(tools.offset(), tools.limit(), tools.total()));
        }
    }

    private static JsonNode json(String value) {
        try {
            return JSON.readTree(value);
        } catch (Exception ignored) {
            return JSON.createObjectNode();
        }
    }
}
