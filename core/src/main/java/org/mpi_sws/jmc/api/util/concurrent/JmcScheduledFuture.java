package org.mpi_sws.jmc.api.util.concurrent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils;

import java.util.concurrent.*;

/**
 * A scheduled future that runs a callable or runnable function in a new thread.
 * Implements {@link RunnableScheduledFuture} for JMC model checking.
 *
 * <p>In JMC's controlled execution, delays are not modeled - all scheduled tasks
 * execute immediately. The delay information is stored but not used for actual timing.
 *
 * @param <T> The return type of the callable function.
 */
public class JmcScheduledFuture<T> implements RunnableScheduledFuture<T> {

    private static final Logger LOGGER = LogManager.getLogger(JmcScheduledFuture.class);

    private final CompletableFuture<T> future;
    private final Long taskId;
    private final JmcThread thread;
    private final long delay;  // Stored but not used in JMC
    private final TimeUnit unit;  // Stored but not used in JMC
    private volatile boolean cancelled = false;
    private volatile boolean periodic = false;

    /**
     * Creates a scheduled future from a Callable with a new task ID.
     */
    public JmcScheduledFuture(Callable<T> function, Long taskId) {
        this(function, taskId, 0, TimeUnit.NANOSECONDS, false);
    }

    /**
     * Creates a scheduled future from a Callable with delay information.
     */
    public JmcScheduledFuture(Callable<T> function, Long taskId, long delay, TimeUnit unit, boolean periodic) {
        this.future = new CompletableFuture<>();
        JmcRuntimeUtils.writeEventWithoutYield(
                this.future,
                false, "java/util/concurrent/CompletableFuture", "result", "Z");
        JmcRuntime.yield();
        this.taskId = taskId;
        this.delay = delay;
        this.unit = unit;
        this.periodic = periodic;
        this.thread = new JmcThread(
                () -> {
                    try {
                        set(function.call());
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                },
                taskId);
    }

    /**
     * Creates a scheduled future from a Runnable with a new task ID.
     */
    public JmcScheduledFuture(Runnable runnable, Long taskId) {
        this(runnable, taskId, 0, TimeUnit.NANOSECONDS, false);
    }

    /**
     * Creates a scheduled future from a Runnable with delay information.
     */
    public JmcScheduledFuture(Runnable runnable, Long taskId, long delay, TimeUnit unit, boolean periodic) {
        this.future = new CompletableFuture<>();
        JmcRuntimeUtils.writeEventWithoutYield(
                this.future,
                false, "java/util/concurrent/CompletableFuture", "result", "Z");
        JmcRuntime.yield();
        this.taskId = taskId;
        this.delay = delay;
        this.unit = unit;
        this.periodic = periodic;
        this.thread = new JmcThread(
                () -> {
                    try {
                        runnable.run();
                        set(null);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                },
                taskId);
    }

    /**
     * Creates a scheduled future from a Runnable with a result value.
     */
    public JmcScheduledFuture(Runnable runnable, T result, Long taskId) {
        this(runnable, result, taskId, 0, TimeUnit.NANOSECONDS, false);
    }

    /**
     * Creates a scheduled future from a Runnable with a result value and delay information.
     */
    public JmcScheduledFuture(Runnable runnable, T result, Long taskId, long delay, TimeUnit unit, boolean periodic) {
        this.future = new CompletableFuture<>();
        JmcRuntimeUtils.writeEventWithoutYield(
                this.future,
                false, "java/util/concurrent/CompletableFuture", "result", "Z");
        JmcRuntime.yield();
        this.taskId = taskId;
        this.delay = delay;
        this.unit = unit;
        this.periodic = periodic;
        this.thread = new JmcThread(
                () -> {
                    try {
                        runnable.run();
                        set(result);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                },
                taskId);
    }

    /**
     * Creates a scheduled future from an existing JmcThread with a result value.
     */
    public JmcScheduledFuture(JmcThread thread, T result) {
        this(thread, result, 0, TimeUnit.NANOSECONDS, false);
    }

    /**
     * Creates a scheduled future from an existing JmcThread with a result value and delay information.
     */
    public JmcScheduledFuture(JmcThread thread, T result, long delay, TimeUnit unit, boolean periodic) {
        this.future = new CompletableFuture<>();
        JmcRuntimeUtils.writeEventWithoutYield(
                this.future,
                false, "java/util/concurrent/CompletableFuture", "result", "Z");
        JmcRuntime.yield();
        this.taskId = thread.getTaskId();
        this.delay = delay;
        this.unit = unit;
        this.periodic = periodic;
        this.thread = new JmcThread(
                () -> {
                    try {
                        thread.run1();
                        set(result);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                },
                taskId);
    }

    /**
     * Creates a scheduled future from an existing JmcThread.
     */
    public JmcScheduledFuture(JmcThread thread) {
        this(thread, 0, TimeUnit.NANOSECONDS, false);
    }

    /**
     * Creates a scheduled future from an existing JmcThread with delay information.
     */
    public JmcScheduledFuture(JmcThread thread, long delay, TimeUnit unit, boolean periodic) {
        this.future = new CompletableFuture<>();
        JmcRuntimeUtils.writeEventWithoutYield(
                this.future,
                false, "java/util/concurrent/CompletableFuture", "result", "Z");
        JmcRuntime.yield();
        this.taskId = thread.getTaskId();
        this.delay = delay;
        this.unit = unit;
        this.periodic = periodic;
        this.thread = thread;
    }

    public Long getTaskId() {
        return taskId;
    }

    /**
     * Returns the remaining delay. In JMC, always returns 0 since delays are not modeled.
     */
    @Override
    public long getDelay(TimeUnit unit) {
        // In JMC, delays are not modeled, so always return 0
        return delay;
    }

    /**
     * Compares this scheduled future with another delayed object.
     * In JMC, all delays are 0, so comparison is based on task ID.
     */
    @Override
    public int compareTo(Delayed other) {
        if (other == this) {
            return 0;
        }
        // In JMC, all delays are 0, so we compare by taskId for determinism
        if (other instanceof JmcScheduledFuture) {
            return Long.compare(this.taskId, ((JmcScheduledFuture<?>) other).taskId);
        }
        // Fall back to delay comparison
        long diff = getDelay(TimeUnit.NANOSECONDS) - other.getDelay(TimeUnit.NANOSECONDS);
        return (diff < 0) ? -1 : (diff > 0) ? 1 : 0;
    }

    /**
     * Returns whether this is a periodic task.
     * In JMC, periodic tasks are executed once, so this is informational only.
     */
    @Override
    public boolean isPeriodic() {
        return periodic;
    }

    /**
     * Cancel the future.
     * Currently, cancellation is limited in JMC - cannot stop running tasks.
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (isDone()) {
            return false;
        }
        cancelled = true;
        future.cancel(mayInterruptIfRunning);
        return true;
    }

    @Override
    public boolean isCancelled() {
        JmcRuntimeUtils.readEventWithoutYield(
                this.future, "java/util/concurrent/CompletableFuture", "result", "Z");
        boolean isCancelled = future.isCancelled() || cancelled;
        JmcRuntime.yield();
        return isCancelled;
    }

    @Override
    public boolean isDone() {
        JmcRuntimeUtils.readEventWithoutYield(
                this.future, "java/util/concurrent/CompletableFuture", "result", "Z");
        boolean done = future.isDone();
        JmcRuntime.yield();
        return done;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        LOGGER.debug("Waiting on scheduled future: {}", thread.getTaskId());
        thread.join1(0L);
        JmcRuntimeUtils.readEventWithoutYield(
                this.future, "java/util/concurrent/CompletableFuture", "result", "Z");
        T result = future.get();
        JmcRuntime.yield();
        return result;
    }

    @Override
    public T get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        long waitTime = unit.toMillis(timeout);
        thread.join1(waitTime);
        LOGGER.debug("Waiting on scheduled future {} with timeout: {}ms", thread.getTaskId(), waitTime);
        // Currently we do not support timeouts, therefore the timeout here is ignored
        JmcRuntimeUtils.readEventWithoutYield(
                this.future, "java/util/concurrent/CompletableFuture", "result", "Z");
        T result = future.get(timeout, unit);
        JmcRuntime.yield();
        return result;
    }

    private void set(T value) {
        future.complete(value);
    }

    /**
     * Run the underlying callable/runnable function in a new thread.
     * This is called by the worker thread.
     */
    @Override
    public void run() {
        if (cancelled) {
            return;
        }
        LOGGER.debug("Starting scheduled future: {}", thread.getTaskId());
        thread.runWithoutJoin();
    }
}
