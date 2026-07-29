package org.oryxos.core.scheduling;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;
import org.oryxos.core.model.Profile;
import org.oryxos.core.model.ScheduleDefinition;
import org.oryxos.core.react.ReActLoop;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

/**
 * Single-process scheduler. Locks are deliberately in-memory and task-scoped;
 * missed executions are not persisted or replayed.
 */
public final class AgentScheduler implements AutoCloseable {

    @FunctionalInterface
    public interface Invocation {
        ReActLoop.Result invoke(String agentName, String scheduleId, String prompt);
    }

    public interface Listener {
        default void completed(String taskId, ReActLoop.Result result) { }
        default void failed(String taskId, RuntimeException failure) { }
        default void skippedOverlap(String taskId) { }
    }

    private final TaskScheduler scheduler;
    private final Invocation invocation;
    private final Listener listener;
    private final Map<String, ScheduledTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public AgentScheduler(TaskScheduler scheduler, ScheduledAgentInvoker invoker) {
        this(scheduler, invoker::invoke, new Listener() { });
    }

    public AgentScheduler(TaskScheduler scheduler, Invocation invocation,
            Listener listener) {
        this.scheduler = java.util.Objects.requireNonNull(scheduler);
        this.invocation = java.util.Objects.requireNonNull(invocation);
        this.listener = java.util.Objects.requireNonNull(listener);
    }

    public synchronized void reload(Collection<Profile> profiles) {
        tasks.values().forEach(task -> task.future().cancel(false));
        tasks.clear();
        for (var profile : profiles == null ? java.util.List.<Profile>of() : profiles) {
            for (var schedule : profile.schedules()) {
                if (schedule.enabled()) {
                    register(profile.name(), schedule);
                }
            }
        }
        locks.keySet().retainAll(tasks.keySet());
    }

    public boolean runNow(String agentName, String scheduleId) {
        var task = tasks.get(taskId(agentName, scheduleId));
        if (task == null) {
            throw new IllegalArgumentException("Unknown scheduled task: "
                    + taskId(agentName, scheduleId));
        }
        return execute(task.agentName(), task.schedule());
    }

    public java.util.Set<String> taskIds() {
        return java.util.Set.copyOf(tasks.keySet());
    }

    @Override
    public synchronized void close() {
        tasks.values().forEach(task -> task.future().cancel(false));
        tasks.clear();
        locks.clear();
    }

    private void register(String agentName, ScheduleDefinition schedule) {
        var id = taskId(agentName, schedule.id());
        if (tasks.containsKey(id)) {
            throw new IllegalStateException("Duplicate scheduled task: " + id);
        }
        var trigger = new CronTrigger(schedule.cron(), schedule.zoneId());
        var future = scheduler.schedule(() -> execute(agentName, schedule), trigger);
        if (future == null) {
            throw new IllegalStateException("Scheduler rejected task: " + id);
        }
        tasks.put(id, new ScheduledTask(agentName, schedule, future, Instant.now()));
    }

    private boolean execute(String agentName, ScheduleDefinition schedule) {
        var id = taskId(agentName, schedule.id());
        var lock = locks.computeIfAbsent(id, ignored -> new ReentrantLock());
        if (!lock.tryLock()) {
            listener.skippedOverlap(id);
            return false;
        }
        try {
            listener.completed(id, invocation.invoke(
                    agentName, schedule.id(), schedule.prompt()));
            return true;
        } catch (RuntimeException failure) {
            listener.failed(id, failure);
            return false;
        } finally {
            lock.unlock();
        }
    }

    private String taskId(String agentName, String scheduleId) {
        return agentName + "/" + scheduleId;
    }

    private record ScheduledTask(String agentName, ScheduleDefinition schedule,
            ScheduledFuture<?> future, Instant registeredAt) {
    }
}
