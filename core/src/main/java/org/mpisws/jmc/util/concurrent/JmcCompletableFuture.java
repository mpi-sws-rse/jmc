package org.mpisws.jmc.util.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public class JmcCompletableFuture<T> extends CompletableFuture<T> {
    static final Executor executor = new JmcExecutorService(2);

    public JmcCompletableFuture() {
        super();
    }

    @Override
    public <U> CompletableFuture<U> newIncompleteFuture() {
        return new JmcCompletableFuture<U>();
    }

    @Override
    public Executor defaultExecutor() {
        return executor;
    }

    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, executor);
    }

    // TODO: This is a realistic use case of time in executing tasks. Need to figure out how
    //    to handle this.
    //
    //    public static Executor delayedExecutor() {
    //        return new JmcExecutorService(2);
    //    }
}
