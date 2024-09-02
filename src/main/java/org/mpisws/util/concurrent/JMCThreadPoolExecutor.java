package org.mpisws.util.concurrent;

import org.jetbrains.annotations.NotNull;
import org.mpisws.manager.HaltExecutionException;
import org.mpisws.runtime.RuntimeEnvironment;

import java.util.concurrent.*;

public class JMCThreadPoolExecutor extends ThreadPoolExecutor {

    public JMCThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, @NotNull TimeUnit unit, @NotNull BlockingQueue<Runnable> workQueue, @NotNull ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    public JMCThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, @NotNull TimeUnit unit, @NotNull BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    public JMCThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, @NotNull TimeUnit unit, @NotNull BlockingQueue<Runnable> workQueue, @NotNull RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    public JMCThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, @NotNull TimeUnit unit, @NotNull BlockingQueue<Runnable> workQueue, @NotNull ThreadFactory threadFactory, @NotNull RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        //RuntimeEnvironment.threadStart(t, Thread.currentThread());
        super.beforeExecute(t, r);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        try {
            RuntimeEnvironment.finishThreadRequest(Thread.currentThread());
            super.afterExecute(r, t);
        } catch (HaltExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void execute(Runnable command) {

        if (!(command instanceof RunnableFuture<?>)) {
            System.out.println("[JMCThreadPoolExecutor Message] : The next command is not a FutureRunnable");
            System.exit(0);
        }
        Future future = (Future) command;
        Runnable wrappedCommand = () -> {
            RuntimeEnvironment.addFuture(future, Thread.currentThread());
            RuntimeEnvironment.waitRequest(Thread.currentThread());
            command.run();
        };
        super.execute(wrappedCommand);
    }

    @NotNull
    @Override
    public <T> Future<T> submit(@NotNull Callable<T> task) {
        return super.submit(task);
    }
}
