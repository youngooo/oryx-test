package org.oryxos.core.memory;

import java.time.Instant;

public record LongTermMemoryView(
        String id,
        MemoryScope scope,
        String content,
        Instant createdAt) {
}
