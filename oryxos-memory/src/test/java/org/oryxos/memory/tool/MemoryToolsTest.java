package org.oryxos.memory.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.oryxos.core.memory.MemoryScope;
import org.oryxos.core.memory.LongTermMemoryView;
import org.oryxos.core.model.Profile;
import org.oryxos.core.model.Session;
import org.oryxos.core.port.MemoryService;
import org.oryxos.core.tool.ToolExecutionContext;

class MemoryToolsTest {
    @TempDir Path temp;
    @Test void exposesSchemasAndDefaultsSaveToArchival() {
        var memory = new Stub();
        var tools = MemoryTools.create(memory, new ObjectMapper());
        var context = new ToolExecutionContext("s", "a", temp,
                Instant.now().plusSeconds(1), "cli", "u", java.util.Map.of());
        var save = tools.getFirst();
        var result = save.execute(new ObjectMapper().createObjectNode()
                .put("content", "remember me"), context);
        assertThat(result.success()).isTrue();
        assertThat(memory.scope).isEqualTo(MemoryScope.ARCHIVAL);
        assertThat(tools).extracting(tool -> tool.getName())
                .containsExactly("save_memory", "recall_memory");
        assertThat(save.getInputSchema().path("required").toString())
                .contains("content");
        assertThat(tools.get(1).getInputSchema().path("properties")
                .has("keyword")).isTrue();

        var coreResult = save.execute(new ObjectMapper().createObjectNode()
                .put("content", "core fact").put("scope", "CORE"), context);
        assertThat(coreResult.success()).isTrue();
        assertThat(memory.scope).isEqualTo(MemoryScope.CORE);

        memory.recalled = List.of(new LongTermMemoryView(
                "m1", MemoryScope.CORE, "core fact", Instant.EPOCH));
        var recallResult = tools.get(1).execute(
                new ObjectMapper().createObjectNode()
                        .put("keyword", "core").put("maxChars", 100), context);
        assertThat(recallResult.success()).isTrue();
        assertThat(recallResult.content()).contains("[CORE] core fact");

        memory.failure = true;
        assertThat(save.execute(new ObjectMapper().createObjectNode()
                .put("content", "fail"), context).success()).isFalse();
    }

    private static final class Stub implements MemoryService {
        MemoryScope scope;
        List<LongTermMemoryView> recalled = List.of();
        boolean failure;
        public org.oryxos.core.memory.MemoryContext loadContext(String id, Profile p) {
            return new org.oryxos.core.memory.MemoryContext(List.of(), List.of(), "");
        }
        public Session appendSessionMessage(String id, org.oryxos.core.model.Message m) {
            throw new UnsupportedOperationException();
        }
        public void remember(String content, MemoryScope scope) {
            if (failure) {
                throw new IllegalStateException("memory unavailable");
            }
            this.scope = scope;
        }
        public List<LongTermMemoryView> recallLongTerm(
                String keyword, int maxChars) {
            if (failure) {
                throw new IllegalStateException("memory unavailable");
            }
            return recalled;
        }
    }
}
