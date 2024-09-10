package org.mpisws.util.concurrent;

import org.jetbrains.annotations.NotNull;
import org.mpisws.manager.HaltExecutionException;
import org.mpisws.runtime.RuntimeEnvironment;

import java.util.concurrent.*;

public class JMCThreadPoolExecutor extends ThreadPoolExecutor {

    public JMCThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, @NotNull TimeUnit unit, @NotNull BlockingQueue<Runnable> workQueue, @NotNull ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        RuntimeEnvironment.setWorkQueue(workQueue);
    }

    public JMCThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, @NotNull TimeUnit unit, @NotNull BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        RuntimeEnvironment.setWorkQueue(workQueue);
    }

    public JMCThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, @NotNull TimeUnit unit, @NotNull BlockingQueue<Runnable> workQueue, @NotNull RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
        RuntimeEnvironment.setWorkQueue(workQueue);
    }

    public JMCThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, @NotNull TimeUnit unit, @NotNull BlockingQueue<Runnable> workQueue, @NotNull ThreadFactory threadFactory, @NotNull RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
        RuntimeEnvironment.setWorkQueue(workQueue);
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        //RuntimeEnvironment.threadStart(t, Thread.currentThread());
        super.beforeExecute(t, r);
        RuntimeEnvironment.taskAssignToThread(t, r);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        RuntimeEnvironment.taskDissociateFromThread(Thread.currentThread(), r);
        RuntimeEnvironment.waitRequest(Thread.currentThread());
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
            command.run();
        };
        super.execute(wrappedCommand);
    }

    @NotNull
    @Override
    public <T> Future<T> submit(@NotNull Callable<T> task) {
        return super.submit(task);
    }

    /**
     * @param callable
     * @param <T>
     * @return
     */
    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return new JMCFutureTask(callable);
    }

    /**
     * @param runnable
     * @param value
     * @param <T>
     * @return
     */
    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return new JMCFutureTask(runnable, value);
    }
}