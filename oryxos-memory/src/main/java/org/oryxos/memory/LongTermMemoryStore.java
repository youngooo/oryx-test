package org.oryxos.memory;

import java.util.List;
import org.oryxos.core.memory.LongTermMemoryView;
import org.oryxos.core.memory.MemoryScope;

public interface LongTermMemoryStore {
    int MAX_ENTRY_CHARS = 32 * 1024;

    void append(String content, MemoryScope scope);
    List<LongTermMemoryView> load();
    List<LongTermMemoryView> recallByKeyword(String keyword);
}
