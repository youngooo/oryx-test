package org.oryxos.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.Executors;
import org.oryxos.core.memory.MemoryScope;

public final class LongTermMemoryStoreContract {
    private LongTermMemoryStoreContract() { }

    public static void verify(LongTermMemoryStore store) {
        assertThatThrownBy(() -> store.append(" ", MemoryScope.CORE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.append(
                "x".repeat(32 * 1024 + 1), MemoryScope.CORE))
                .isInstanceOf(IllegalArgumentException.class);
        store.append("favorite color blue", MemoryScope.CORE);
        store.append("favorite color blue", MemoryScope.CORE);
        store.append("weather is mild", MemoryScope.ARCHIVAL);
        assertThat(store.load()).extracting(item -> item.content())
                .containsExactly("favorite color blue", "weather is mild");
        assertThat(store.recallByKeyword("color"))
                .extracting(item -> item.content())
                .containsExactly("favorite color blue");

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int index = 0; index < 8; index++) {
                var content = "concurrent-" + index;
                executor.submit(() ->
                        store.append(content, MemoryScope.ARCHIVAL));
            }
        }
        assertThat(store.load()).hasSize(10);
    }
}
