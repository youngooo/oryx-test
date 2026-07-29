package org.oryxos.core.session;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Serializes complete turns for one Session while allowing different Sessions
 * to execute concurrently.
 */
public final class SessionCoordinator {

    private final ConcurrentHashMap<String, ReentrantLock> locks =
            new ConcurrentHashMap<>();

    public <T> T execute(String sessionId, Supplier<T> action) {
        var lock = locks.computeIfAbsent(sessionId,
                ignored -> new ReentrantLock(true));
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) {
                locks.remove(sessionId, lock);
            }
        }
    }

    public int activeCount() {
        return locks.size();
    }
}
