package org.oryxos.storage.adapter;

import org.oryxos.core.model.LlmCallRecord;
import org.oryxos.core.model.ToolInvocationRecord;
import org.oryxos.core.port.InvocationAuditStore;
import org.oryxos.storage.entity.LlmCallEntity;
import org.oryxos.storage.entity.ToolInvocationEntity;
import org.oryxos.storage.repository.LlmCallRepository;
import org.oryxos.storage.repository.ToolInvocationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaInvocationAuditStore implements InvocationAuditStore {

    private final LlmCallRepository llmCalls;
    private final ToolInvocationRepository toolInvocations;

    public JpaInvocationAuditStore(LlmCallRepository llmCalls,
            ToolInvocationRepository toolInvocations) {
        this.llmCalls = llmCalls;
        this.toolInvocations = toolInvocations;
    }

    @Override
    @Transactional
    public void saveLlmCall(LlmCallRecord record) {
        llmCalls.saveAndFlush(toEntity(record));
    }

    @Override
    @Transactional
    public void saveToolInvocation(ToolInvocationRecord record) {
        toolInvocations.saveAndFlush(toEntity(record));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LlmCallRecord> findLlmCalls(String sessionId, int offset, int limit) {
        checkPage(offset, limit);
        var items = llmCalls.findPageBySessionId(sessionId, offset, limit);
        return new Page<>(items.stream().map(this::toDomain).toList(),
                offset, limit, llmCalls.countBySessionId(sessionId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ToolInvocationRecord> findToolInvocations(
            String sessionId, int offset, int limit) {
        checkPage(offset, limit);
        var items = toolInvocations.findPageBySessionId(sessionId, offset, limit);
        return new Page<>(items.stream().map(this::toDomain).toList(),
                offset, limit, toolInvocations.countBySessionId(sessionId));
    }

    private void checkPage(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException(
                    "offset must be non-negative; limit is 1..100");
        }
    }

    private LlmCallEntity toEntity(LlmCallRecord value) {
        return new LlmCallEntity(value.id(), value.sessionId(), value.provider(),
                value.model(), value.iteration(), value.promptTokens(),
                value.completionTokens(), value.success(), value.finishReason(),
                value.errorCode(), value.errorMessage(), value.startedAt(),
                value.completedAt(), value.durationMs());
    }

    private LlmCallRecord toDomain(LlmCallEntity value) {
        return new LlmCallRecord(value.getId(), value.getSessionId(),
                value.getProvider(), value.getModel(), value.getIteration(),
                value.getPromptTokens(), value.getCompletionTokens(), value.isSuccess(),
                value.getFinishReason(), value.getErrorCode(), value.getErrorMessage(),
                value.getStartedAt(), value.getCompletedAt(), value.getDurationMs());
    }

    private ToolInvocationEntity toEntity(ToolInvocationRecord value) {
        return new ToolInvocationEntity(value.id(), value.sessionId(), value.toolName(),
                value.argumentsJson(), value.success(), value.resultSummary(),
                value.errorCode(), value.errorMessage(), value.retryable(),
                value.startedAt(), value.completedAt(), value.durationMs());
    }

    private ToolInvocationRecord toDomain(ToolInvocationEntity value) {
        return new ToolInvocationRecord(value.getId(), value.getSessionId(),
                value.getToolName(), value.getArgumentsJson(), value.isSuccess(),
                value.getResultSummary(), value.getErrorCode(), value.getErrorMessage(),
                value.isRetryable(), value.getStartedAt(), value.getCompletedAt(),
                value.getDurationMs());
    }
}
