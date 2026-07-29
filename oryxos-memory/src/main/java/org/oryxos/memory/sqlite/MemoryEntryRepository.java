package org.oryxos.memory.sqlite;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryEntryRepository
        extends JpaRepository<MemoryEntryEntity, String> {
    boolean existsByScopeAndContent(String scope, String content);
    List<MemoryEntryEntity> findAllByOrderByCreatedAtAscIdAsc();
}
