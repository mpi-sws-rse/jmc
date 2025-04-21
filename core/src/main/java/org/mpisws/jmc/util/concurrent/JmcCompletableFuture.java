package org.mpisws.jmc.util.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinTask;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class JmcCompletableFuture<T> extends CompletableFuture<T> {
    private static final JmcExecutorService executor = new JmcExecutorService(2);

    private JmcFuture<T> underlyingFuture;

    public JmcCompletableFuture() {
        super();
    }

    private JmcCompletableFuture(JmcFuture<T> underlyingFuture) {
        super();
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

    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier, Executor executor) {
        return asyncSupplyStage(executor, supplier);
    }

    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        return runAsync(runnable, executor);
    }

    public static CompletableFuture<Void> runAsync(Runnable runnable, Executor executor) {
        return asyncRunStage(executor, runnable);
    }

    @SuppressWarnings("serial")
    static final class AsyncSupply<T> extends ForkJoinTask<Void>
            implements Runnable, AsynchronousCompletionTask {
        JmcCompletableFuture<T> dep;
        Supplier<? extends T> fn;

        AsyncSupply(JmcCompletableFuture<T> dep, Supplier<? extends T> fn) {
            this.dep = dep;
            this.fn = fn;
        }

        public final Void getRawResult() {
            return null;
        }

        public final void setRawResult(Void v) {
        }

        public final boolean exec() {
            run();
            return false;
        }

        public void run() {
            CompletableFuture<T> d;
            Supplier<? extends T> f;
            if ((d = dep) != null && (f = fn) != null) {
                dep = null;
                fn = null;
                try {
                    d.complete(f.get());
                } catch (Throwable ex) {
                    d.completeExceptionally(ex);
                }
            }
        }
    }

    static <U> CompletableFuture<U> asyncSupplyStage(Executor e,
                                                     Supplier<U> f) {
        if (f == null) throw new NullPointerException();
        JmcCompletableFuture<U> d = new JmcCompletableFuture<>();
        e.execute(new AsyncSupply<U>(d, f));
        return d;
    }

    @SuppressWarnings("serial")
    static final class AsyncRun extends ForkJoinTask<Void>
            implements Runnable, AsynchronousCompletionTask {
        JmcCompletableFuture<Void> dep;
        Runnable fn;

        AsyncRun(JmcCompletableFuture<Void> dep, Runnable fn) {
            this.dep = dep;
            this.fn = fn;
        }

        public final Void getRawResult() {
            return null;
        }

        public final void setRawResult(Void v) {
        }

        public final boolean exec() {
            run();
            return false;
        }

        public void run() {
            CompletableFuture<Void> d;
            Runnable f;
            if ((d = dep) != null && (f = fn) != null) {
                dep = null;
                fn = null;
                try {
                    f.run();
                    d.complete(null);
                } catch (Throwable ex) {
                    d.completeExceptionally(ex);
                }
            }
        }
    }

    static CompletableFuture<Void> asyncRunStage(JmcExecutorService e, Runnable f) {
        if (f == null) throw new NullPointerException();
        JmcCompletableFuture<Void> d = new JmcCompletableFuture<>();
        e.execute();
        return d;
    }

    @Override
    public CompletableFuture<Void> thenAccept(Consumer<? super T> action) {

        return super.thenAcceptAsync(action, executor);
    }

}
