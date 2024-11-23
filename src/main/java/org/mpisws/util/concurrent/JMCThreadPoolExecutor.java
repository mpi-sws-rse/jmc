package org.mpisws.util.concurrent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.mpisws.runtime.JmcRuntime;
import org.mpisws.runtime.RuntimeEvent;
import org.mpisws.runtime.RuntimeEventType;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class JMCThreadPoolExecutor extends ThreadPoolExecutor {

    private static final Logger LOGGER = LogManager.getLogger(JMCThreadPoolExecutor.class);

    public JMCThreadPoolExecutor(
            int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime,
            @NotNull TimeUnit unit,
            @NotNull BlockingQueue<Runnable> workQueue,
            @NotNull ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        updateJmcRuntimeSetWorkQueue(workQueue);
    }

    private void updateJmcRuntimeSetWorkQueue(@NotNull BlockingQueue<Runnable> workQueue) {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.THREAD_POOL_CREATED)
                        .taskId(JmcRuntime.currentTask())
                        .param("threadPool", this)
                        .param("workQueue", workQueue)
                        .build();
        JmcRuntime.updateEvent(event);
    }

    public JMCThreadPoolExecutor(
            int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime,
            @NotNull TimeUnit unit,
            @NotNull BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        updateJmcRuntimeSetWorkQueue(workQueue);
    }

    public JMCThreadPoolExecutor(
            int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime,
            @NotNull TimeUnit unit,
            @NotNull BlockingQueue<Runnable> workQueue,
            @NotNull RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
        updateJmcRuntimeSetWorkQueue(workQueue);
    }

    public JMCThreadPoolExecutor(
            int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime,
            @NotNull TimeUnit unit,
            @NotNull BlockingQueue<Runnable> workQueue,
            @NotNull ThreadFactory threadFactory,
            @NotNull RejectedExecutionHandler handler) {
        super(
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                unit,
                workQueue,
                threadFactory,
                handler);
        updateJmcRuntimeSetWorkQueue(workQueue);
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.TASK_ASSIGNED_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("task", r)
                        .param("thread", t)
                        .build();
        JmcRuntime.updateEvent(event);
        super.beforeExecute(t, r);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        // TODO: need a new event?
        if (Thread.currentThread() instanceof JmcThread jmcThread) {
            jmcThread.hasTask = false;
        } else {
            LOGGER.error("The current thread is not a JMC starter thread");
            System.exit(0);
        }
        // RuntimeEnvironment.taskDissociateFromThread(Thread.currentThread(), r);
        super.afterExecute(r, t);
    }

    /**
     * TODO: complete this
     *
     * @param callable
     * @param <T>
     * @return
     */
    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        JMCFutureTask jmcFutureTask = new JMCFutureTask(callable);
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.TASK_CREATED_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("task", jmcFutureTask)
                        .build();
        JmcRuntime.updateEvent(event);
        return jmcFutureTask;
    }

    /**
     * TODO: complete this
     *
     * @param runnable
     * @param value
     * @param <T>
     * @return
     */
    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        JMCFutureTask jmcFutureTask = new JMCFutureTask(runnable, value);
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.TASK_CREATED_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("task", jmcFutureTask)
                        .build();
        JmcRuntime.updateEvent(event);
        return jmcFutureTask;
    }

    /** */
    @Override
    public void shutdown() {
        LOGGER.debug("The thread pool is shutting down.");
        // TODO() : Add support for handling the shutdown
        // super.shutdown();
    }
}
