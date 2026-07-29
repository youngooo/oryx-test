package org.oryxos.core.model;

import java.time.Instant;
import java.util.List;

public record Message(
        String messageId,
        long sequence,
        Role role,
        String content,
        String toolCallId,
        String toolName,
        List<ToolRequest> toolRequests,
        Instant createdAt) {

    public enum Role { SYSTEM, USER, ASSISTANT, TOOL }

    public Message(String messageId, long sequence, Role role, String content,
            String toolCallId, String toolName, Instant createdAt) {
        this(messageId, sequence, role, content, toolCallId, toolName,
                List.of(), createdAt);
    }

    public Message {
        messageId = Profile.requireText(messageId, "messageId");
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        if (role == null) {
            throw new IllegalArgumentException("role is required");
        }
        content = Profile.requireText(content, "content");
        if (role == Role.USER && content.length() > 32 * 1024) {
            throw new IllegalArgumentException("user message exceeds 32 KiB");
        }
        toolCallId = Profile.normalize(toolCallId);
        toolName = Profile.normalize(toolName);
        toolRequests = List.copyOf(toolRequests == null ? List.of() : toolRequests);
        if (role == Role.TOOL && (toolCallId == null || toolName == null)) {
            throw new IllegalArgumentException("Tool messages require call id and name");
        }
        if (!toolRequests.isEmpty() && role != Role.ASSISTANT) {
            throw new IllegalArgumentException(
                    "Only assistant messages may contain Tool requests");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt is required");
        }
    }

    public record ToolRequest(String id, String name, String argumentsJson) {
        public ToolRequest {
            id = Profile.requireText(id, "tool request id");
            name = Profile.requireText(name, "tool request name");
            argumentsJson = Profile.requireText(
                    argumentsJson, "tool request arguments");
        }
    }
}
