package org.oryxos.memory.sqlite;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.oryxos.core.memory.LongTermMemoryView;
import org.oryxos.core.memory.MemoryScope;
import org.oryxos.memory.LongTermMemoryStore;
import org.oryxos.memory.MemoryStoreSupport;
import org.springframework.transaction.annotation.Transactional;

public class SqliteMemoryStore implements LongTermMemoryStore {
    private final MemoryEntryRepository repository;

    public SqliteMemoryStore(MemoryEntryRepository repository) {
        this.repository = java.util.Objects.requireNonNull(repository);
    }

    @Override
    @Transactional
    public synchronized void append(String content, MemoryScope scope) {
        var value = MemoryStoreSupport.validate(content, scope);
        if (!repository.existsByScopeAndContent(scope.name(), value)) {
            repository.saveAndFlush(new MemoryEntryEntity(
                    UUID.randomUUID().toString(), scope.name(), value,
                    Instant.now().toString()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public synchronized List<LongTermMemoryView> load() {
        return repository.findAllByOrderByCreatedAtAscIdAsc().stream()
                .map(entity -> new LongTermMemoryView(entity.getId(),
                        MemoryScope.valueOf(entity.getScope()),
                        entity.getContent(), Instant.parse(entity.getCreatedAt())))
                .toList();
    }

    @Override
    public List<LongTermMemoryView> recallByKeyword(String keyword) {
        return MemoryStoreSupport.recall(load(), keyword);
    }
}
