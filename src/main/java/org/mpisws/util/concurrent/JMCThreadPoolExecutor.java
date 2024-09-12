package org.mpisws.util.concurrent;

import org.jetbrains.annotations.NotNull;
import org.mpisws.manager.HaltExecutionException;
import org.mpisws.runtime.RuntimeEnvironment;

import java.util.concurrent.*;

public class JMCThreadPoolExecutor extends ThreadPoolExecutor {

    public int id;

    public JMCThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, @NotNull TimeUnit unit, @NotNull BlockingQueue<Runnable> workQueue, @NotNull ThreadFactory threadFactory, int id) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        this.id = id;
        RuntimeEnvironment.setWorkQueue(id, workQueue);
    }

    public JMCThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, @NotNull TimeUnit unit, @NotNull BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        id = RuntimeEnvironment.nextThreadPoolExecutorId();
        RuntimeEnvironment.setWorkQueue(id, workQueue);
    }

    public JMCThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, @NotNull TimeUnit unit, @NotNull BlockingQueue<Runnable> workQueue, @NotNull RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
        id = RuntimeEnvironment.nextThreadPoolExecutorId();
        RuntimeEnvironment.setWorkQueue(id, workQueue);
    }

    public JMCThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, @NotNull TimeUnit unit, @NotNull BlockingQueue<Runnable> workQueue, @NotNull ThreadFactory threadFactory, @NotNull RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
        id = RuntimeEnvironment.nextThreadPoolExecutorId();
        RuntimeEnvironment.setWorkQueue(id, workQueue);
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        RuntimeEnvironment.threadRunningNewTask(t, r);
        super.beforeExecute(t, r);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        if (Thread.currentThread() instanceof JMCStarterThread jmcStarterThread) {
            jmcStarterThread.hasTask = false;
        } else {
            System.out.println("[JMCThreadPoolExecutor Message] : The current thread is not a JMC starter thread");
            System.exit(0);
        }
        //RuntimeEnvironment.taskDissociateFromThread(Thread.currentThread(), r);
        super.afterExecute(r, t);
    }

    @Override
    public void execute(Runnable command) {
        super.execute(command);
    }

    private void oldExecute(Runnable command) {
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
        JMCFutureTask jmcFutureTask = new JMCFutureTask(callable);
        RuntimeEnvironment.newTaskCreated(Thread.currentThread(), jmcFutureTask, id);
        return jmcFutureTask;
    }

    /**
     * @param runnable
     * @param value
     * @param <T>
     * @return
     */
    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        JMCFutureTask jmcFutureTask = new JMCFutureTask(runnable, value);
        RuntimeEnvironment.newTaskCreated(Thread.currentThread(), jmcFutureTask, id);
        return jmcFutureTask;
    }
}