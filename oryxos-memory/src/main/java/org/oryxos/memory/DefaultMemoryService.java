package org.oryxos.memory;

import java.util.List;
import java.util.Objects;
import org.oryxos.core.memory.LongTermMemoryView;
import org.oryxos.core.memory.MemoryContext;
import org.oryxos.core.memory.MemoryScope;
import org.oryxos.core.model.Message;
import org.oryxos.core.model.Profile;
import org.oryxos.core.model.Session;
import org.oryxos.core.port.MemoryService;
import org.oryxos.core.session.SessionManager;

public final class DefaultMemoryService implements MemoryService {

    private final SessionManager sessions;
    private final LongTermMemoryStore longTerm;

    public DefaultMemoryService(
            SessionManager sessions, LongTermMemoryStore longTerm) {
        this.sessions = Objects.requireNonNull(sessions);
        this.longTerm = Objects.requireNonNull(longTerm);
    }

    @Override
    public MemoryContext loadContext(String sessionId, Profile profile) {
        return new MemoryContext(sessions.history(sessionId, 20),
                longTerm.load(), "");
    }

    @Override
    public Session appendSessionMessage(String sessionId, Message message) {
        return sessions.append(sessionId, message);
    }

    @Override
    public void remember(String content, MemoryScope scope) {
        longTerm.append(content, scope);
    }

    @Override
    public List<LongTermMemoryView> recallLongTerm(
            String keyword, int maxChars) {
        if (maxChars < 1) {
            throw new IllegalArgumentException("maxChars must be positive");
        }
        var remaining = maxChars;
        var result = new java.util.ArrayList<LongTermMemoryView>();
        for (var item : longTerm.recallByKeyword(keyword)) {
            if (remaining == 0) {
                break;
            }
            var content = item.content();
            if (content.length() > remaining) {
                content = content.substring(0, remaining);
            }
            result.add(new LongTermMemoryView(item.id(), item.scope(),
                    content, item.createdAt()));
            remaining -= content.length();
        }
        return List.copyOf(result);
    }

    @Override
    public List<LongTermMemoryView> loadLongTerm() {
        return longTerm.load();
    }
}
