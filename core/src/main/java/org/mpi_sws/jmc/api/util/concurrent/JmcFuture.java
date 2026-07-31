package org.mpi_sws.jmc.api.util.concurrent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils;

import java.util.concurrent.*;

/**
 * A future that runs a callable/runnable on a dedicated {@link JmcThread} task under JMC control.
 *
 * <p>Replacement for {@code FutureTask}/{@code RunnableFuture}. It wraps the work in a {@code
 * JmcThread} bound to a runtime task id and stores the result in a backing {@link CompletableFuture}
 * whose {@code result} field's reads/writes are reported to the runtime. {@code get()} joins the
 * worker task before reading the result. Used by the JMC executor services; cancellation and
 * timeouts are not yet supported.
 *
 * @param <T> The return type of the callable function.
 */
public class JmcFuture<T> implements RunnableFuture<T> {
    // TODO: Add support for cancellation and timeouts.

    /** Logger for future lifecycle diagnostics. */
    private static final Logger LOGGER = LogManager.getLogger(JmcFuture.class);

    /** Backing completable future holding the result; its {@code result} field is reported to the runtime. */
    private final CompletableFuture<T> future;
    /** Runtime task id of the worker thread running this future. */
    private final Long taskId;
    /** The JMC thread that runs the wrapped work and completes {@link #future}. */
    private final JmcThread thread;

    /**
     * Creates a future that runs a callable and completes with its result.
     *
     * @param function the callable to run
     * @param taskId the runtime task id for the worker thread
     */
    public JmcFuture(Callable<T> function, Long taskId) {
        this.future = new CompletableFuture<>();
        JmcRuntimeUtils.writeEventWithoutYield(
               this.future ,
                false, "java/util/concurrent/CompletableFuture", "result", "Z");
        JmcRuntime.yield();
        this.taskId = taskId;
        this.thread =
                new JmcThread(
                        () -> {
                            try {
                                set(function.call());
                                return;
                            } catch (Exception e) {
                                future.completeExceptionally(e);
                            }
                        },
                        taskId);
    }

    /**
     * Creates a future that runs a runnable and completes with {@code null}.
     *
     * @param runnable the runnable to run
     * @param taskId the runtime task id for the worker thread
     */
    public JmcFuture(Runnable runnable, Long taskId) {
        this.future = new CompletableFuture<>();
        JmcRuntimeUtils.writeEventWithoutYield(
                this.future ,
                false, "java/util/concurrent/CompletableFuture", "result", "Z");
        JmcRuntime.yield();
        this.taskId = taskId;
        this.thread =
                new JmcThread(
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
     * Creates a future that runs a runnable and completes with the given result.
     *
     * @param runnable the runnable to run
     * @param result the value to complete with
     * @param taskId the runtime task id for the worker thread
     */
    public JmcFuture(Runnable runnable, T result, Long taskId) {
        this.future = new CompletableFuture<>();
        JmcRuntimeUtils.writeEventWithoutYield(
                this.future ,
                false, "java/util/concurrent/CompletableFuture", "result", "Z");
        JmcRuntime.yield();
        this.taskId = taskId;
        this.thread =
                new JmcThread(
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
     * Creates a future that runs an existing {@link JmcThread}'s body and completes with the given
     * result, reusing that thread's task id.
     *
     * @param thread the JMC thread whose body to run
     * @param result the value to complete with
     */
    public JmcFuture(JmcThread thread, T result) {
        this.future = new CompletableFuture<>();
        JmcRuntimeUtils.writeEventWithoutYield(
                this.future ,
                false, "java/util/concurrent/CompletableFuture", "result", "Z");
        JmcRuntime.yield();
        this.taskId = thread.getTaskId();
        this.thread =
                new JmcThread(
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
     * Creates a future backed directly by an existing {@link JmcThread} (reusing its task id).
     *
     * @param thread the JMC thread to run
     */
    public JmcFuture(JmcThread thread) {
        this.future = new CompletableFuture<>();
        JmcRuntimeUtils.writeEventWithoutYield(
                this.future ,
                false, "java/util/concurrent/CompletableFuture", "result", "Z");
        JmcRuntime.yield();
        this.taskId = thread.getTaskId();
        this.thread = thread;
    }

    /**
     * Returns the runtime task id of the worker thread running this future.
     *
     * @return the task id
     */
    public Long getTaskId() {
        return taskId;
    }

    /**
     * Cancel the future.
     *
     * <p>Currently unsupported by Jmc. Cannot stop tasks yet.
     *
     * @param b Whether to interrupt the future.
     * @return Whether the future was successfully cancelled.
     */
    @Override
    public boolean cancel(boolean b) {
        return false;
    }

    /**
     * Returns whether the future was cancelled (reporting a read of the backing future's state and
     * yielding).
     *
     * @return whether the future is cancelled
     */
    @Override
    public boolean isCancelled() {
        JmcRuntimeUtils.readEventWithoutYield(
                this.future, "java/util/concurrent/CompletableFuture", "result", "Z");
        boolean cancelled = future.isCancelled();
        JmcRuntime.yield();
        return cancelled;
    }

    /**
     * Returns whether the future is done (reporting a read of the backing future's state and
     * yielding).
     *
     * @return whether the future is complete
     */
    @Override
    public boolean isDone() {
        JmcRuntimeUtils.readEventWithoutYield(
                this.future, "java/util/concurrent/CompletableFuture", "result", "Z");
        boolean done = future.isDone();
        JmcRuntime.yield();
        return done;
    }

    /**
     * Waits for the task to finish and returns its result.
     *
     * <p>Joins the worker thread (via {@code join1}), reports a read of the result, and yields before
     * returning it.
     *
     * @return the computed result
     * @throws InterruptedException if the join is interrupted
     * @throws ExecutionException if the task completed exceptionally
     */
    @Override
    public T get() throws InterruptedException, ExecutionException {
        LOGGER.debug("Waiting on future: {}", thread.getTaskId());
        thread.join1(0L);
        JmcRuntimeUtils.readEventWithoutYield(
                this.future, "java/util/concurrent/CompletableFuture", "result", "Z");
        T result = future.get();
        JmcRuntime.yield();
        return result;
    }

    /**
     * Waits for the task and returns its result. The timeout is accepted for API compatibility but is
     * currently not enforced.
     *
     * @param l the timeout magnitude (ignored)
     * @param timeUnit the timeout unit (ignored)
     * @return the computed result
     * @throws InterruptedException if the join is interrupted
     * @throws ExecutionException if the task completed exceptionally
     * @throws TimeoutException declared for API compatibility
     */
    @Override
    public T get(long l, TimeUnit timeUnit)
            throws InterruptedException, ExecutionException, TimeoutException {
        long waitTime = timeUnit.toMillis(l);
        thread.join1(waitTime);
        LOGGER.debug("Waiting on future {} with timeout: {}ms", thread.getTaskId(), waitTime);
        // Currently we do not support timeouts, therefore the timeout here is ignored
        JmcRuntimeUtils.readEventWithoutYield(
                this.future, "java/util/concurrent/CompletableFuture", "result", "Z");
        T result = future.get(l, timeUnit);
        JmcRuntime.yield();
        return result;
    }

    /**
     * Completes the backing future with the given value.
     *
     * @param value the result to complete with
     */
    private void set(T value) {
        future.complete(value);
    }

    /** Run the underlying callable function in a new thread. */
    public void run() {
        LOGGER.debug("Starting future: {}", thread.getTaskId());
        thread.runWithoutJoin();
    }
}
