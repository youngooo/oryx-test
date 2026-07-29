package org.oryxos.memory.markdown;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.oryxos.core.memory.LongTermMemoryView;
import org.oryxos.core.memory.MemoryScope;
import org.oryxos.memory.LongTermMemoryStore;
import org.oryxos.memory.MemoryStoreSupport;

public final class MarkdownMemoryStore implements LongTermMemoryStore {
    private static final String CORE = "## Core";
    private static final String ARCHIVAL = "## Archival";
    private final Path file;
    private final int archivalLimit;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    public MarkdownMemoryStore(Path file) {
        this(file, 64 * 1024);
    }

    public MarkdownMemoryStore(Path file, int archivalLimit) {
        this.file = file.toAbsolutePath().normalize();
        if (archivalLimit < 1) {
            throw new IllegalArgumentException("archivalLimit must be positive");
        }
        this.archivalLimit = archivalLimit;
    }

    @Override
    public void append(String content, MemoryScope scope) {
        var value = MemoryStoreSupport.validate(content, scope);
        lock.writeLock().lock();
        try {
            var entries = new ArrayList<>(readAll());
            if (entries.stream().anyMatch(item ->
                    item.scope() == scope && item.content().equals(value))) {
                return;
            }
            entries.add(new LongTermMemoryView(UUID.randomUUID().toString(),
                    scope, value, Instant.now()));
            writeAll(entries);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<LongTermMemoryView> load() {
        lock.readLock().lock();
        try {
            var all = readAll();
            var core = all.stream().filter(e -> e.scope() == MemoryScope.CORE).toList();
            var archival = all.stream()
                    .filter(e -> e.scope() == MemoryScope.ARCHIVAL).toList();
            var kept = new ArrayList<LongTermMemoryView>();
            var remaining = archivalLimit;
            for (int index = archival.size() - 1; index >= 0; index--) {
                var item = archival.get(index);
                if (item.content().length() <= remaining) {
                    kept.addFirst(item);
                    remaining -= item.content().length();
                }
            }
            var result = new ArrayList<>(core);
            result.addAll(kept);
            return List.copyOf(result);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<LongTermMemoryView> recallByKeyword(String keyword) {
        return MemoryStoreSupport.recall(load(), keyword);
    }

    private List<LongTermMemoryView> readAll() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            var result = new ArrayList<LongTermMemoryView>();
            var scope = MemoryScope.CORE;
            for (var line : Files.readAllLines(file)) {
                var value = line.strip();
                if (value.equalsIgnoreCase(CORE)) {
                    scope = MemoryScope.CORE;
                } else if (value.equalsIgnoreCase(ARCHIVAL)) {
                    scope = MemoryScope.ARCHIVAL;
                } else if (value.startsWith("- ")) {
                    var content = value.substring(2).strip();
                    if (!content.isEmpty()) {
                        result.add(new LongTermMemoryView(
                                UUID.nameUUIDFromBytes(
                                        (scope + ":" + content).getBytes(
                                                java.nio.charset.StandardCharsets.UTF_8))
                                        .toString(),
                                scope, content,
                                Files.getLastModifiedTime(file).toInstant()));
                    }
                }
            }
            return List.copyOf(result);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to read Markdown memory", failure);
        }
    }

    private void writeAll(List<LongTermMemoryView> entries) {
        try {
            Files.createDirectories(file.getParent());
            var lines = new ArrayList<String>();
            lines.add("# OryxOS Memory");
            lines.add("");
            lines.add(CORE);
            entries.stream().filter(e -> e.scope() == MemoryScope.CORE)
                    .forEach(e -> lines.add("- " + oneLine(e.content())));
            lines.add("");
            lines.add(ARCHIVAL);
            entries.stream().filter(e -> e.scope() == MemoryScope.ARCHIVAL)
                    .forEach(e -> lines.add("- " + oneLine(e.content())));
            Files.write(file, lines);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to write Markdown memory", failure);
        }
    }

    private String oneLine(String value) {
        return value.replace("\r", " ").replace("\n", " ");
    }
}
