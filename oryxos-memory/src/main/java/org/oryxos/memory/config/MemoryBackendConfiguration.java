package org.oryxos.memory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import org.oryxos.memory.LongTermMemoryStore;
import org.oryxos.memory.markdown.MarkdownMemoryStore;
import org.oryxos.memory.mem0.Mem0MemoryStore;
import org.oryxos.memory.sqlite.MemoryEntryRepository;
import org.oryxos.memory.sqlite.SqliteMemoryStore;

public final class MemoryBackendConfiguration {
    private MemoryBackendConfiguration() { }

    public static LongTermMemoryStore select(String backend, Path workspace,
            MemoryEntryRepository repository, URI mem0BaseUri,
            String mem0Credential, ObjectMapper mapper) {
        var selected = backend == null || backend.isBlank()
                ? "markdown" : backend.strip().toLowerCase(Locale.ROOT);
        return switch (selected) {
            case "markdown" -> new MarkdownMemoryStore(
                    workspace.resolve("memory/MEMORY.md"));
            case "sqlite" -> {
                if (repository == null) {
                    throw new IllegalStateException(
                            "SQLite memory repository is unavailable");
                }
                yield new SqliteMemoryStore(repository);
            }
            case "mem0" -> new Mem0MemoryStore(mem0BaseUri,
                    mem0Credential, mapper);
            default -> throw new IllegalArgumentException(
                    "Unknown memory backend: " + selected);
        };
    }
}
