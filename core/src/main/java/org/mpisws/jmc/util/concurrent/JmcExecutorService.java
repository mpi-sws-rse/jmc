package org.mpisws.jmc.util.concurrent;

import org.mpisws.jmc.runtime.JmcRuntime;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An executor service that runs tasks in new threads.
 *
 * <p>Currently, the executor service does not support stopping tasks.
 */
public class JmcExecutorService implements ExecutorService {

    /** Stops the executor service. Currently not supported. */
    @Override
    public void shutdown() {}

    /** Stops the executor service. Currently not supported. */
    @Override
    public List<Runnable> shutdownNow() {
        return List.of();
    }

    /** Returns whether the executor service is shutdown. */
    @Override
    public boolean isShutdown() {
        return false;
    }

    /** Returns whether the executor service is terminated. */
    @Override
    public boolean isTerminated() {
        return false;
    }

    /** Waits for the executor service to terminate. */
    @Override
    public boolean awaitTermination(long l, TimeUnit timeUnit) throws InterruptedException {
        return false;
    }

    /** Submits a callable task to the executor service. */
    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        JmcFuture<T> future = new JmcFuture<>(callable, JmcRuntime.addNewTask());
        future.run();
        return future;
    }

    @Override
    public <T> Future<T> submit(Runnable runnable, T t) {
        JmcFuture<T> future =
                new JmcFuture<>(
                        () -> {
                            runnable.run();
                            return t;
                        },
                        JmcRuntime.addNewTask());
        future.run();
        return future;
    }

    @Override
    public Future<?> submit(Runnable runnable) {
        JmcFuture<?> future =
                new JmcFuture<>(
                        () -> {
                            runnable.run();
                            return null;
                        },
                        JmcRuntime.addNewTask());
        future.run();
        return future;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> collection)
            throws InterruptedException {
        // Map each callable to a future and run them
        return collection.stream()
                .map(
                        callable -> {
                            JmcFuture<T> future =
                                    new JmcFuture<>(callable, JmcRuntime.addNewTask());
                            future.run();
                            return (Future<T>) future;
                        })
                .toList();
    }

    @Override
    public <T> List<Future<T>> invokeAll(
            Collection<? extends Callable<T>> collection, long l, TimeUnit timeUnit)
            throws InterruptedException {
        return collection.stream()
                .map(
                        callable -> {
                            JmcFuture<T> future =
                                    new JmcFuture<>(callable, JmcRuntime.addNewTask());
                            future.run();
                            return (Future<T>) future;
                        })
                .toList();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> collection)
            throws InterruptedException, ExecutionException {
        return null;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> collection, long l, TimeUnit timeUnit)
            throws InterruptedException, ExecutionException, TimeoutException {
        // Currently we do not support timeouts, therefore the timeout here is ignored
        List<JmcFuture<T>> futures =
                collection.stream()
                        .map(
                                callable -> {
                                    JmcFuture<T> future =
                                            new JmcFuture<>(callable, JmcRuntime.addNewTask());
                                    future.run();
                                    return future;
                                })
                        .toList();
        while (true) {
            for (JmcFuture<T> future : futures) {
                if (future.isDone()) {
                    try {
                        return future.get();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @Override
    public void execute(Runnable runnable) {
        JmcThread thread = new JmcThread(runnable, JmcRuntime.addNewTask());
        thread.start();
    }
}
