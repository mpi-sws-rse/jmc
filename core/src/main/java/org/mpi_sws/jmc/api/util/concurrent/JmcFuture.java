package org.mpi_sws.jmc.api.util.concurrent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils;

import java.util.concurrent.*;

/**
 * A future that runs a callable function in a new thread.
 *
 * @param <T> The return type of the callable function.
 */
public class JmcFuture<T> implements RunnableFuture<T> {
    // TODO: Add support for cancellation and timeouts.

    private static final Logger LOGGER = LogManager.getLogger(JmcFuture.class);

    private final CompletableFuture<T> future;
    private final Long taskId;
    private final JmcThread thread;
    //2 writw events 1. result

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

    public JmcFuture(JmcThread thread) {
        this.future = new CompletableFuture<>();
        JmcRuntimeUtils.writeEventWithoutYield(
                this.future ,
                false, "java/util/concurrent/CompletableFuture", "result", "Z");
        JmcRuntime.yield();
        this.taskId = thread.getTaskId();
        this.thread = thread;
    }

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

    @Override
    public boolean isCancelled() {
        JmcRuntimeUtils.readEventWithoutYield(
                this.future, "java/util/concurrent/CompletableFuture", "result", "Z");
        boolean cancelled = future.isCancelled();
        JmcRuntime.yield();
        return cancelled;
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
        LOGGER.debug("Waiting on future: {}", thread.getTaskId());
        thread.join1(0L);
        JmcRuntimeUtils.readEventWithoutYield(
                this.future, "java/util/concurrent/CompletableFuture", "result", "Z");
        T result = future.get();
        JmcRuntime.yield();
        return result;
    }

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

    private void set(T value) {
        future.complete(value);
    }

    /** Run the underlying callable function in a new thread. */
    public void run() {
        LOGGER.debug("Starting future: {}", thread.getTaskId());
        thread.runWithoutJoin();
    }
}
