package org.mpisws.util.concurrent;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

public class JmcExecutorService implements ExecutorService {

    @Override
    public void shutdown() {

    }

    @NotNull
    @Override
    public List<Runnable> shutdownNow() {
        return List.of();
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(long l, @NotNull TimeUnit timeUnit) throws InterruptedException {
        return false;
    }

    @NotNull
    @Override
    public <T> Future<T> submit(@NotNull Callable<T> callable) {
        return null;
    }

    @NotNull
    @Override
    public <T> Future<T> submit(@NotNull Runnable runnable, T t) {
        return null;
    }

    @NotNull
    @Override
    public Future<?> submit(@NotNull Runnable runnable) {
        return null;
    }

    @NotNull
    @Override
    public <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> collection) throws InterruptedException {
        return List.of();
    }

    @NotNull
    @Override
    public <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> collection, long l, @NotNull TimeUnit timeUnit) throws InterruptedException {
        return List.of();
    }

    @NotNull
    @Override
    public <T> T invokeAny(@NotNull Collection<? extends Callable<T>> collection) throws InterruptedException, ExecutionException {
        return null;
    }

    @Override
    public <T> T invokeAny(@NotNull Collection<? extends Callable<T>> collection, long l, @NotNull TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
        return null;
    }

    @Override
    public void execute(@NotNull Runnable runnable) {

    }
}
