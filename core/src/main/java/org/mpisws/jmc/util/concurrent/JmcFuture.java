package org.mpisws.jmc.util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A future that runs a callable function in a new thread.
 *
 * @param <T> The return type of the callable function.
 */
public class JmcFuture<T> implements Future<T> {
    private CompletableFuture<T> future;
    private Long taskId;
    private JmcThread thread;

    // TODO: Add a constructor that has a particular result value to return.
    // TODO: Add a constructor to take in a runnable and check if the runnable is already a
    //   JmcThread, reuse the taskId in that case.
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
        thread.join1(0L);
        return future.get();
    }

    @Override
    public T get(long l, TimeUnit timeUnit)
            throws InterruptedException, ExecutionException, TimeoutException {
        // Currently we do not support timeouts, therefore the timeout here is ignored
        thread.join1(0L);
        return future.get(l, timeUnit);
    }

    private void set(T value) {
        future.complete(value);
    }

    /** Run the underlying callable function in a new thread. */
    public void run() {
        thread.start();
    }
}
