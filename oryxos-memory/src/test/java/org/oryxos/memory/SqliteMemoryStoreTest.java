package org.oryxos.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.oryxos.memory.sqlite.MemoryEntryEntity;
import org.oryxos.memory.sqlite.MemoryEntryRepository;
import org.oryxos.memory.sqlite.SqliteMemoryStore;

class SqliteMemoryStoreTest {
    @Test void satisfiesSharedContract() {
        var values = new ArrayList<MemoryEntryEntity>();
        var repository = mock(MemoryEntryRepository.class);
        when(repository.existsByScopeAndContent(any(), any())).thenAnswer(call ->
                values.stream().anyMatch(item ->
                        item.getScope().equals(call.getArgument(0))
                                && item.getContent().equals(call.getArgument(1))));
        when(repository.saveAndFlush(any())).thenAnswer(call -> {
            var value = call.<MemoryEntryEntity>getArgument(0);
            values.add(value);
            return value;
        });
        when(repository.findAllByOrderByCreatedAtAscIdAsc())
                .thenAnswer(call -> List.copyOf(values));
        LongTermMemoryStoreContract.verify(new SqliteMemoryStore(repository));
    }
}
