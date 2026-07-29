package org.oryxos.web.controller;

import java.time.Instant;
import java.util.Comparator;
import org.oryxos.core.memory.MemoryScope;
import org.oryxos.core.port.MemoryService;
import org.oryxos.web.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/memory")
public class MemoryApiController {
    private final MemoryService memory;

    public MemoryApiController(MemoryService memory) {
        this.memory = memory;
    }

    @GetMapping
    public ApiResponse<MemoryDocument> get() {
        var entries = memory.loadLongTerm();
        var core = join(entries, MemoryScope.CORE);
        var archival = join(entries, MemoryScope.ARCHIVAL);
        var modified = entries.stream()
                .map(org.oryxos.core.memory.LongTermMemoryView::createdAt)
                .max(Comparator.naturalOrder()).orElse(Instant.EPOCH);
        return ApiResponse.ok(new MemoryDocument(core, archival, modified));
    }

    private String join(
            java.util.List<org.oryxos.core.memory.LongTermMemoryView> entries,
            MemoryScope scope) {
        return entries.stream().filter(value -> value.scope() == scope)
                .map(org.oryxos.core.memory.LongTermMemoryView::content)
                .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
    }

    public record MemoryDocument(
            String core, String archival, Instant lastModifiedAt) {
    }
}
