package org.mpi_sws.jmc.api.util.concurrent;

import org.mpi_sws.jmc.runtime.JmcRuntime;

import java.util.concurrent.*;

/**
 * A thread pool executor that runs tasks in new threads. The thread creation is wrapped with the
 * {@link JmcThreadFactory} to create {@link JmcThread} instances. Reimplementation of {@link
 * java.util.concurrent.ThreadPoolExecutor}
 */
public class JmcThreadPoolExecutor extends ThreadPoolExecutor {

    /**
     * Creates a fixed-size pool of {@code nThreads} JMC worker threads with an unbounded queue.
     *
     * @param nThreads the core and maximum pool size
     */
    public JmcThreadPoolExecutor(int nThreads) {
        super(
                nThreads,
                nThreads,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new JmcThreadFactory());
    }

    /**
     * Creates a pool with the given sizing and work queue, using a {@link JmcThreadFactory}.
     *
     * @param corePoolSize the core pool size
     * @param maximumPoolSize the maximum pool size
     * @param keepAliveTime idle keep-alive time
     * @param unit the unit of {@code keepAliveTime}
     * @param workQueue the work queue
     */
    public JmcThreadPoolExecutor(
            int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime,
            TimeUnit unit,
            BlockingQueue<Runnable> workQueue) {
        super(
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                unit,
                workQueue,
                new JmcThreadFactory());
    }

    /**
     * As above, wrapping the supplied thread factory in a {@link JmcThreadFactory}.
     *
     * @param corePoolSize the core pool size
     * @param maximumPoolSize the maximum pool size
     * @param keepAliveTime idle keep-alive time
     * @param unit the unit of {@code keepAliveTime}
     * @param workQueue the work queue
     * @param threadFactory the base thread factory to wrap
     */
    public JmcThreadPoolExecutor(
            int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime,
            TimeUnit unit,
            BlockingQueue<Runnable> workQueue,
            ThreadFactory threadFactory) {
        super(
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                unit,
                workQueue,
                new JmcThreadFactory(threadFactory));
    }

    /**
     * As above, with a rejected-execution handler and a default {@link JmcThreadFactory}.
     *
     * @param corePoolSize the core pool size
     * @param maximumPoolSize the maximum pool size
     * @param keepAliveTime idle keep-alive time
     * @param unit the unit of {@code keepAliveTime}
     * @param workQueue the work queue
     * @param handler the rejected-execution handler
     */
    public JmcThreadPoolExecutor(
            int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime,
            TimeUnit unit,
            BlockingQueue<Runnable> workQueue,
            RejectedExecutionHandler handler) {
        super(
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                unit,
                workQueue,
                new JmcThreadFactory(),
                handler);
    }

    /**
     * As above, with both a wrapped thread factory and a rejected-execution handler.
     *
     * @param corePoolSize the core pool size
     * @param maximumPoolSize the maximum pool size
     * @param keepAliveTime idle keep-alive time
     * @param unit the unit of {@code keepAliveTime}
     * @param workQueue the work queue
     * @param threadFactory the base thread factory to wrap
     * @param handler the rejected-execution handler
     */
    public JmcThreadPoolExecutor(
            int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime,
            TimeUnit unit,
            BlockingQueue<Runnable> workQueue,
            ThreadFactory threadFactory,
            RejectedExecutionHandler handler) {
        super(
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                unit,
                workQueue,
                new JmcThreadFactory(threadFactory),
                handler);
    }

    /**
     * Wraps a callable submission in a {@link JmcFuture} bound to a fresh runtime task.
     *
     * @param callable the submitted callable
     * @return a {@code JmcFuture} for the task
     */
    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return new JmcFuture<>(callable, JmcRuntime.addNewTask());
    }


    /**
     * Wraps a runnable submission (with a result) in a {@link JmcFuture} bound to a fresh runtime
     * task.
     *
     * @param runnable the submitted runnable
     * @param value the result to return on completion
     * @return a {@code JmcFuture} for the task
     */
    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return new JmcFuture<>(runnable, value, JmcRuntime.addNewTask());
    }

}
