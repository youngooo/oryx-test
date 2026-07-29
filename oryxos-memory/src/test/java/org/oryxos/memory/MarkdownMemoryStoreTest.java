package org.oryxos.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.oryxos.core.memory.MemoryScope;
import org.oryxos.memory.markdown.MarkdownMemoryStore;

class MarkdownMemoryStoreTest {
    @TempDir Path temp;

    @Test void satisfiesSharedContract() {
        LongTermMemoryStoreContract.verify(
                new MarkdownMemoryStore(temp.resolve("MEMORY.md")));
    }

    @Test void retainsAllCoreMemoryAndOnlyNewestArchivalContentWithinLimit() {
        var store = new MarkdownMemoryStore(temp.resolve("MEMORY.md"), 10);
        store.append("core-content-that-is-never-truncated", MemoryScope.CORE);
        store.append("old-value", MemoryScope.ARCHIVAL);
        store.append("new", MemoryScope.ARCHIVAL);

        assertThat(store.load()).extracting(item -> item.content())
                .containsExactly("core-content-that-is-never-truncated", "new");
    }

    @Test void serializesConcurrentAppendsWithoutLosingEntries() throws Exception {
        var store = new MarkdownMemoryStore(temp.resolve("MEMORY.md"));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int index = 0; index < 20; index++) {
                var value = "entry-" + index;
                executor.submit(() -> store.append(value, MemoryScope.ARCHIVAL));
            }
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(store.load()).hasSize(20);
    }
}
