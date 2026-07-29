package org.oryxos.memory;

import java.util.List;
import java.util.Locale;
import org.oryxos.core.memory.LongTermMemoryView;
import org.oryxos.core.memory.MemoryScope;

public final class MemoryStoreSupport {
    private MemoryStoreSupport() { }

    public static String validate(String content, MemoryScope scope) {
        if (scope == null) {
            throw new IllegalArgumentException("memory scope is required");
        }
        var normalized = content == null ? "" : content.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("memory content is required");
        }
        if (normalized.length() > LongTermMemoryStore.MAX_ENTRY_CHARS) {
            throw new IllegalArgumentException("memory content exceeds 32 KiB");
        }
        return normalized;
    }

    public static List<LongTermMemoryView> recall(
            List<LongTermMemoryView> entries, String keyword) {
        var needle = keyword == null ? "" : keyword.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return List.copyOf(entries);
        }
        return entries.stream()
                .filter(item -> item.content().toLowerCase(Locale.ROOT)
                        .contains(needle))
                .toList();
    }
}
