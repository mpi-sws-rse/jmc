package org.mpi_sws.jmc.api.util.concurrent;

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
    /** Shared JMC executor that runs async stages so their tasks are scheduled by JMC. */
    private static final JmcExecutorService executor = new JmcExecutorService(2);

    /** The JMC future backing an async stage, if any. */
    private JmcFuture<T> underlyingFuture;

    /** Creates an incomplete JMC completable future with no backing future. */
    public JmcCompletableFuture() {
        super();
        this.underlyingFuture = null;
    }

    /**
     * Sets the JMC future backing this stage.
     *
     * @param underlyingFuture the backing future
     */
    public void setUnderlyingFuture(JmcFuture<T> underlyingFuture) {
        this.underlyingFuture = underlyingFuture;
    }

    /**
     * Returns a new incomplete future of this JMC type, so dependent stages stay under JMC control.
     *
     * @return a new {@code JmcCompletableFuture}
     */
    @Override
    public <U> CompletableFuture<U> newIncompleteFuture() {
        return new JmcCompletableFuture<U>();
    }

    /**
     * Returns the default executor for async stages: the shared JMC executor.
     *
     * @return the JMC executor
     */
    @Override
    public Executor defaultExecutor() {
        return executor;
    }

    /**
     * Runs a supplier asynchronously on the JMC executor, completing the returned future with its
     * result.
     *
     * @param supplier the supplier to run
     * @return a future completing with the supplied value
     */
    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier) {
        return asyncSupplyStage(executor, supplier);
    }

    /**
     * Runs a runnable asynchronously on the JMC executor, completing the returned future when it
     * finishes.
     *
     * @param runnable the runnable to run
     * @return a future completing when the runnable finishes
     */
    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        return runAsync(runnable, executor);
    }

    /**
     * A runnable that computes an async stage's value (from a supplier) and completes the associated
     * {@link JmcCompletableFuture}, completing it exceptionally on error.
     *
     * @param <T> the result type
     */
    public static class JmcAsyncRunnable<T> implements Runnable {
        /** The supplier producing the stage's value (mutually exclusive with {@link #runnable}). */
        private final Supplier<? extends T> supplier;
        /** The future to complete with the produced value. */
        private final JmcCompletableFuture<T> future;
        /** An alternative runnable body (unused when a supplier is given). */
        private final Runnable runnable;

        /**
         * @param supplier the supplier producing the stage value
         * @param future the future to complete
         */
        public JmcAsyncRunnable(Supplier<? extends T> supplier, JmcCompletableFuture<T> future) {
            this.supplier = supplier;
            this.future = future;
            this.runnable = null;
        }

        /**
         * Sets the backing JMC future on the associated completable future.
         *
         * @param underlyingFuture the backing future
         */
        public void setUnderlyingFuture(JmcFuture<T> underlyingFuture) {
            this.future.setUnderlyingFuture(underlyingFuture);
        }

        /** Runs the supplier (or runnable) and completes the future, or completes it exceptionally on error. */
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

    /**
     * Submits a supplier to the given JMC executor and returns a {@code JmcCompletableFuture} that
     * completes with its result (or exceptionally on error).
     *
     * @param e the executor to run on
     * @param f the supplier
     * @return the future for the async stage
     */
    static <U> CompletableFuture<U> asyncSupplyStage(JmcExecutorService e, Supplier<U> f) {
        if (f == null) throw new NullPointerException();
        JmcCompletableFuture<U> d = new JmcCompletableFuture<>();
        JmcFuture underlyingFuture =
                e.submit(
                        new Runnable() {
                            // Runs the supplier on the executor and completes the stage future with
                            // its value, or exceptionally if it throws.
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

    /**
     * Submits a runnable to the given JMC executor and returns a {@code JmcCompletableFuture} that
     * completes when it finishes (or exceptionally on error).
     *
     * @param e the executor to run on
     * @param f the runnable
     * @return the future for the async stage
     */
    static CompletableFuture<Void> asyncRunStage(JmcExecutorService e, Runnable f) {
        if (f == null) throw new NullPointerException();
        JmcCompletableFuture<Void> d = new JmcCompletableFuture<>();
        JmcFuture underlyingFuture =
                e.submit(
                        new Runnable() {
                            // Runs the runnable on the executor and completes the stage future with
                            // null, or exceptionally if it throws.
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
