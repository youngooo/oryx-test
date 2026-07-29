package org.oryxos.core.port;

import org.oryxos.core.memory.MemoryContext;
import org.oryxos.core.memory.MemoryScope;
import org.oryxos.core.memory.LongTermMemoryView;
import org.oryxos.core.model.Message;
import org.oryxos.core.model.Profile;
import org.oryxos.core.model.Session;

public interface MemoryService {
    MemoryContext loadContext(String sessionId, Profile profile);
    Session appendSessionMessage(String sessionId, Message message);
    void remember(String content, MemoryScope scope);

    default void appendLongTerm(String content, MemoryScope scope) {
        remember(content, scope);
    }

    default java.util.List<LongTermMemoryView> recallLongTerm(
            String keyword, int maxChars) {
        return java.util.List.of();
    }

    /**
     * Returns the backend-neutral long-term memory view for operational reads.
     * Callers must use this facade instead of reaching into a backend directly.
     */
    default java.util.List<LongTermMemoryView> loadLongTerm() {
        return java.util.List.of();
    }
}
