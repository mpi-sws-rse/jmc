package org.mpisws.jmc.api.util.concurrent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.*;

/**
 * A future that runs a callable function in a new thread.
 *
 * @param <T> The return type of the callable function.
 */
public class JmcFuture<T> implements RunnableFuture<T> {
    // TODO: Add support for cancellation and timeouts.

    private static final Logger LOGGER = LogManager.getLogger(JmcFuture.class);

    private CompletableFuture<T> future;
    private Long taskId;
    private JmcThread thread;

    public JmcFuture(Callable<T> function, Long taskId) {
        this.future = new CompletableFuture<>();
        this.taskId = taskId;
        this.thread =
                new JmcThread(
                        () -> {
                            try {
                                set(function.call());
                            } catch (Exception e) {
                                future.completeExceptionally(e);
                            }
                        },
                        taskId);
    }

    public JmcFuture(Runnable runnable, Long taskId) {
        this.future = new CompletableFuture<>();
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
        return future.isCancelled();
    }

    @Override
    public boolean isDone() {
        return future.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        LOGGER.debug("Waiting on future: {}", thread.getTaskId());
        thread.join1(0L);
        return future.get();
    }

    @Override
    public T get(long l, TimeUnit timeUnit)
            throws InterruptedException, ExecutionException, TimeoutException {
        // Currently we do not support timeouts, therefore the timeout here is ignored
        long waitTime = timeUnit.toMillis(l);
        LOGGER.debug("Waiting on future {} with timeout: {}ms", thread.getTaskId(), waitTime);
        thread.join1(waitTime);
        return future.get(l, timeUnit);
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
