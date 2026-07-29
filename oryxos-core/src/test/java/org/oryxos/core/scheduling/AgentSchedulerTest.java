package org.oryxos.core.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.oryxos.core.model.Profile;
import org.oryxos.core.model.ScheduleDefinition;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class AgentSchedulerTest {

    private final ThreadPoolTaskScheduler taskScheduler = scheduler();

    @AfterEach
    void shutdown() {
        taskScheduler.shutdown();
    }

    @Test
    void registersCronAndPreventsOverlapWhileAllowingManualReplay()
            throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var scheduler = new AgentScheduler(taskScheduler,
                (agent, id, prompt) -> {
                    calls.incrementAndGet();
                    entered.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                }, new AgentScheduler.Listener() { });
        scheduler.reload(List.of(profile()));
        assertThat(scheduler.taskIds()).containsExactly("weather/daily");

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> scheduler.runNow("weather", "daily"));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(scheduler.runNow("weather", "daily")).isFalse();
            release.countDown();
            assertThat(first.get(2, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(calls).hasValue(1);
        assertThat(scheduler.runNow("weather", "daily")).isTrue();
        assertThat(calls).hasValue(2);
        scheduler.close();
    }

    @Test
    void isolatesFailureAndReloadDoesNotReplayMissedRuns() {
        var failures = new AtomicInteger();
        var scheduler = new AgentScheduler(taskScheduler,
                (agent, id, prompt) -> {
                    throw new IllegalStateException("provider down");
                }, new AgentScheduler.Listener() {
                    @Override
                    public void failed(String taskId, RuntimeException failure) {
                        failures.incrementAndGet();
                    }
                });
        scheduler.reload(List.of(profile()));
        assertThat(scheduler.runNow("weather", "daily")).isFalse();
        assertThat(failures).hasValue(1);

        scheduler.reload(List.of(profile()));
        assertThat(failures).hasValue(1);
        assertThat(scheduler.taskIds()).containsExactly("weather/daily");
        scheduler.close();
    }

    private Profile profile() {
        return new Profile("weather", "deepseek", null, Set.of("http_get"),
                Set.of("scheduler"), null, List.of(new ScheduleDefinition(
                        "daily", "0 0 7 * * *", ZoneId.of("Asia/Shanghai"),
                        "weather", true)), 10, null);
    }

    private ThreadPoolTaskScheduler scheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.initialize();
        return scheduler;
    }
}
