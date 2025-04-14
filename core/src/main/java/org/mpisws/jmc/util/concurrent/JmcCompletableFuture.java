package org.mpisws.jmc.util.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

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
}
