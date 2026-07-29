package org.oryxos.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.oryxos.core.memory.MemoryScope;
import org.oryxos.core.model.Message;
import org.oryxos.core.model.Profile;
import org.oryxos.core.model.Session;
import org.oryxos.core.port.SessionStore;
import org.oryxos.core.session.SessionManager;
import org.oryxos.memory.markdown.MarkdownMemoryStore;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class DefaultMemoryServiceTest {
    @TempDir Path temp;
    @Test void composesSessionLongTermAndEmptyEpisodicLayers() {
        var sessions = new Store();
        var now = Instant.parse("2026-07-29T00:00:00Z");
        sessions.save(new Session("s", "a", "cli", "u",
                List.of(new Message("m", 1, Message.Role.USER,
                        "hello", null, null, now)),
                Session.Status.ACTIVE, now, now, null, 0));
        var longTerm = new MarkdownMemoryStore(temp.resolve("MEMORY.md"));
        longTerm.append("likes tea", MemoryScope.CORE);
        var service = new DefaultMemoryService(
                new SessionManager(sessions), longTerm);

        var context = service.loadContext("s", new Profile("a", "p", null,
                java.util.Set.of(), java.util.Set.of("cli"), null,
                List.of(), 10, null));

        assertThat(context.sessionMessages()).hasSize(1);
        assertThat(context.longTermMemory()).extracting(item -> item.content())
                .containsExactly("likes tea");
        assertThat(context.episodicContext()).isEmpty();
    }

    private static final class Store implements SessionStore {
        private final java.util.Map<String, Session> values = new LinkedHashMap<>();
        public Session save(Session value) { values.put(value.sessionId(), value); return value; }
        public Optional<Session> findById(String id) { return Optional.ofNullable(values.get(id)); }
        public Optional<Session> findActive(String p, String c, String u) { return Optional.empty(); }
        public Session append(String id, Message message) {
            return save(values.get(id).append(message));
        }
    }
}
