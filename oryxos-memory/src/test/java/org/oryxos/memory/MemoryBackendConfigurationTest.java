package org.oryxos.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.oryxos.core.memory.MemoryScope;
import org.oryxos.memory.config.MemoryBackendConfiguration;
import org.oryxos.memory.markdown.MarkdownMemoryStore;
import org.oryxos.memory.mem0.Mem0MemoryStore;
import org.oryxos.memory.sqlite.MemoryEntryRepository;
import org.oryxos.memory.sqlite.SqliteMemoryStore;

class MemoryBackendConfigurationTest {
    @TempDir Path temp;
    @Test void selectsEveryBackendAndRejectsInvalidConfiguration() {
        var mapper = new ObjectMapper();
        assertThat(MemoryBackendConfiguration.select(null, temp, null,
                URI.create("http://localhost:8000"), "", mapper))
                .isInstanceOf(MarkdownMemoryStore.class);
        var repository = mock(MemoryEntryRepository.class);
        when(repository.findAllByOrderByCreatedAtAscIdAsc())
                .thenReturn(java.util.List.of());
        assertThat(MemoryBackendConfiguration.select("sqlite", temp, repository,
                URI.create("http://localhost:8000"), "", mapper))
                .isInstanceOf(SqliteMemoryStore.class);
        assertThat(MemoryBackendConfiguration.select("mem0", temp, repository,
                URI.create("http://localhost:8000/api/"), "token", mapper))
                .isInstanceOf(Mem0MemoryStore.class);
        assertThatThrownBy(() -> MemoryBackendConfiguration.select(
                "unknown", temp, null, URI.create("http://localhost:8000"),
                "", mapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown memory backend");
        assertThatThrownBy(() -> MemoryBackendConfiguration.select(
                "sqlite", temp, null, URI.create("http://localhost:8000"),
                "", mapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("repository is unavailable");
        assertThatThrownBy(() -> MemoryBackendConfiguration.select(
                "mem0", temp, repository, URI.create("file:/tmp/mem0"),
                "never-log-this-token", mapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("never-log-this-token");
        assertThat(Mem0MemoryStore.CONNECT_TIMEOUT)
                .isEqualTo(Duration.ofSeconds(3));
        assertThat(Mem0MemoryStore.RESPONSE_TIMEOUT)
                .isEqualTo(Duration.ofSeconds(10));
    }

    @Test void changingBackendDoesNotMigrateExistingMarkdownContent() {
        var markdown = new MarkdownMemoryStore(temp.resolve("memory/MEMORY.md"));
        markdown.append("markdown-only", MemoryScope.CORE);
        var repository = mock(MemoryEntryRepository.class);
        when(repository.findAllByOrderByCreatedAtAscIdAsc())
                .thenReturn(java.util.List.of());

        var sqlite = MemoryBackendConfiguration.select("sqlite", temp,
                repository, URI.create("http://localhost:8000"), "",
                new ObjectMapper());

        assertThat(sqlite.load()).isEmpty();
        assertThat(markdown.load()).extracting(item -> item.content())
                .containsExactly("markdown-only");
    }
}
