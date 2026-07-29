package org.oryxos.core.prompt;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.oryxos.core.agent.ContextLoader;
import org.oryxos.core.memory.MemoryScope;
import org.oryxos.core.model.AgentDefinition;
import org.oryxos.core.model.Message;
import org.oryxos.core.model.Session;
import org.oryxos.core.model.ToolDefinition;
import org.oryxos.core.port.ClockProvider;
import org.oryxos.core.port.MemoryService;
import org.oryxos.core.port.ToolCatalog;

/**
 * Builds deterministic Provider input without reaching into a memory backend.
 */
public final class PromptBuilder {

    public static final int DEFAULT_HISTORY_LIMIT = 20;
    public static final int DEFAULT_CONTEXT_LIMIT = 128 * 1024;

    private final ContextLoader contextLoader;
    private final MemoryService memoryService;
    private final ToolCatalog toolCatalog;
    private final ClockProvider clock;
    private final ZoneId zoneId;
    private final int historyLimit;
    private final int contextLimit;

    public PromptBuilder(ContextLoader contextLoader, MemoryService memoryService,
            ToolCatalog toolCatalog, ClockProvider clock, ZoneId zoneId) {
        this(contextLoader, memoryService, toolCatalog, clock, zoneId,
                DEFAULT_HISTORY_LIMIT, DEFAULT_CONTEXT_LIMIT);
    }

    public PromptBuilder(ContextLoader contextLoader, MemoryService memoryService,
            ToolCatalog toolCatalog, ClockProvider clock, ZoneId zoneId,
            int historyLimit, int contextLimit) {
        this.contextLoader = Objects.requireNonNull(contextLoader);
        this.memoryService = Objects.requireNonNull(memoryService);
        this.toolCatalog = Objects.requireNonNull(toolCatalog);
        this.clock = Objects.requireNonNull(clock);
        this.zoneId = Objects.requireNonNull(zoneId);
        if (historyLimit < 1 || contextLimit < 1) {
            throw new IllegalArgumentException("Prompt limits must be positive");
        }
        this.historyLimit = historyLimit;
        this.contextLimit = contextLimit;
    }

    public PromptContext build(AgentDefinition agent, Session session) {
        Objects.requireNonNull(agent);
        Objects.requireNonNull(session);
        var memory = memoryService.loadContext(session.sessionId(), agent.profile());
        var coreMemory = memory.longTermMemory().stream()
                .filter(item -> item.scope() == MemoryScope.CORE)
                .map(item -> item.content())
                .collect(Collectors.joining("\n"));
        var mandatory = contextLoader.load(agent, clock.now(), zoneId);
        if (mandatory.length() + coreMemory.length() > contextLimit) {
            throw new PromptTooLargeException(
                    "Mandatory Agent and core memory context exceeds prompt limit");
        }

        var archival = memory.longTermMemory().stream()
                .filter(item -> item.scope() == MemoryScope.ARCHIVAL)
                .map(item -> item.content())
                .collect(Collectors.joining("\n"));
        var remaining = contextLimit - mandatory.length() - coreMemory.length();
        if (archival.length() > remaining) {
            archival = archival.substring(archival.length() - remaining);
        }

        var messages = new ArrayList<Message>();
        long sequence = 1;
        messages.add(system(sequence++, mandatory));
        if (!coreMemory.isBlank() || !archival.isBlank()) {
            messages.add(system(sequence++, "Long-term memory\nCORE:\n" + coreMemory
                    + "\nARCHIVAL:\n" + archival));
        }

        var history = memory.sessionMessages();
        var from = Math.max(0, history.size() - historyLimit);
        var bounded = history.subList(from, history.size());
        Message current = null;
        if (!bounded.isEmpty()
                && bounded.get(bounded.size() - 1).role() == Message.Role.USER) {
            current = bounded.get(bounded.size() - 1);
            bounded = bounded.subList(0, bounded.size() - 1);
        }
        for (var message : bounded) {
            messages.add(copyWithSequence(message, sequence++));
        }

        var tools = toolCatalog.availableTo(agent.profile().tools()).stream()
                .map(tool -> new ToolDefinition(tool.getName(), tool.getDescription(),
                        tool.getInputSchema(), ToolDefinition.Origin.BUILT_IN, "oryxos-tool"))
                .toList();
        var toolSummary = tools.isEmpty() ? "Available tools: none"
                : "Available tools:\n" + tools.stream()
                        .map(tool -> tool.name() + ": " + tool.description())
                        .collect(Collectors.joining("\n"));
        messages.add(system(sequence++, toolSummary));
        if (current != null) {
            messages.add(copyWithSequence(current, sequence));
        }
        return new PromptContext(messages, tools);
    }

    private Message system(long sequence, String content) {
        return new Message(java.util.UUID.randomUUID().toString(), sequence,
                Message.Role.SYSTEM, content, null, null, clock.now());
    }

    private Message copyWithSequence(Message source, long sequence) {
        return new Message(source.messageId(), sequence, source.role(), source.content(),
                source.toolCallId(), source.toolName(), source.toolRequests(),
                source.createdAt());
    }

    public record PromptContext(List<Message> messages, List<ToolDefinition> tools) {
        public PromptContext {
            messages = List.copyOf(messages);
            tools = List.copyOf(tools);
        }
    }

    public static final class PromptTooLargeException extends RuntimeException {
        public PromptTooLargeException(String message) {
            super(message);
        }
    }
}
