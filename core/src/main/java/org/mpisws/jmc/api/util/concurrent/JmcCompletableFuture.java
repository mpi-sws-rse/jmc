package org.mpisws.jmc.api.util.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * A JMC-specific version of {@link java.util.concurrent.CompletableFuture} that allows for custom
 * execution and provides a way to set an underlying JmcFuture.
 *
 * @param <T> the type of the result of the future
 */
public class JmcCompletableFuture<T> extends CompletableFuture<T> {
    private static final JmcExecutorService executor = new JmcExecutorService(2);

    private JmcFuture<T> underlyingFuture;

    public JmcCompletableFuture() {
        super();
        this.underlyingFuture = null;
    }

    public void setUnderlyingFuture(JmcFuture<T> underlyingFuture) {
        this.underlyingFuture = underlyingFuture;
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
        return asyncSupplyStage(executor, supplier);
    }

    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        return runAsync(runnable, executor);
    }

    public static class JmcAsyncRunnable<T> implements Runnable {
        private final Supplier<? extends T> supplier;
        private final JmcCompletableFuture<T> future;
        private final Runnable runnable;

        public JmcAsyncRunnable(Supplier<? extends T> supplier, JmcCompletableFuture<T> future) {
            this.supplier = supplier;
            this.future = future;
            this.runnable = null;
        }

        public void setUnderlyingFuture(JmcFuture<T> underlyingFuture) {
            this.future.setUnderlyingFuture(underlyingFuture);
        }

        @Override
        public void run() {
            try {
                if (supplier == null) {
                    runnable.run();
                    future.complete(null);
                } else {
                    future.complete(supplier.get());
                }
            } catch (Throwable ex) {
                future.completeExceptionally(ex);
            }
        }
    }

    static <U> CompletableFuture<U> asyncSupplyStage(JmcExecutorService e, Supplier<U> f) {
        if (f == null) throw new NullPointerException();
        JmcCompletableFuture<U> d = new JmcCompletableFuture<>();
        JmcFuture underlyingFuture =
                e.submit(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    d.complete(f.get());
                                } catch (Throwable ex) {
                                    d.completeExceptionally(ex);
                                }
                            }
                        });
        d.setUnderlyingFuture(underlyingFuture);
        return d;
    }

    static CompletableFuture<Void> asyncRunStage(JmcExecutorService e, Runnable f) {
        if (f == null) throw new NullPointerException();
        JmcCompletableFuture<Void> d = new JmcCompletableFuture<>();
        JmcFuture underlyingFuture =
                e.submit(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    f.run();
                                    d.complete(null);
                                } catch (Throwable ex) {
                                    d.completeExceptionally(ex);
                                }
                            }
                        });
        d.setUnderlyingFuture(underlyingFuture);
        return d;
    }
}
