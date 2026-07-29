package org.oryxos.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class SessionCoordinatorTest {
    @Test void serializesSameSessionAndAllowsDifferentSessions() throws Exception {
        var coordinator = new SessionCoordinator();
        var order = java.util.Collections.synchronizedList(new ArrayList<Integer>());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var firstEntered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var first = executor.submit(() -> coordinator.execute("same", () -> {
                order.add(1); firstEntered.countDown();
                try { release.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                order.add(2); return null;
            }));
            firstEntered.await();
            var second = executor.submit(() -> coordinator.execute("same",
                    () -> { order.add(3); return null; }));
            var other = executor.submit(() -> coordinator.execute("other",
                    () -> { order.add(9); return null; }));
            other.get();
            release.countDown();
            first.get(); second.get();
        }
        assertThat(order.indexOf(9)).isLessThan(order.indexOf(3));
        assertThat(order).containsSubsequence(1, 2, 3);
        assertThat(coordinator.activeCount()).isZero();
    }
}
