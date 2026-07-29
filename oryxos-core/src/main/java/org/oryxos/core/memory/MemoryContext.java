package org.oryxos.core.memory;

import java.util.List;
import org.oryxos.core.model.Message;

public record MemoryContext(
        List<Message> sessionMessages,
        List<LongTermMemoryView> longTermMemory,
        String episodicContext) {

    public MemoryContext {
        sessionMessages = List.copyOf(sessionMessages == null ? List.of() : sessionMessages);
        longTermMemory = List.copyOf(longTermMemory == null ? List.of() : longTermMemory);
        episodicContext = episodicContext == null ? "" : episodicContext;
    }
}
