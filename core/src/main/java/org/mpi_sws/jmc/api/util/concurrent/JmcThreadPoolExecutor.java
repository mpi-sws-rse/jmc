package org.mpi_sws.jmc.api.util.concurrent;

import java.util.concurrent.*;

/**
 * A thread pool executor that runs tasks in new threads. The thread creation is wrapped with the
 * {@link JmcThreadFactory} to create {@link JmcThread} instances. Reimplementation of {@link
 * java.util.concurrent.ThreadPoolExecutor}
 */
public class JmcThreadPoolExecutor extends ThreadPoolExecutor {

    public JmcThreadPoolExecutor(int nThreads) {
        super(
                nThreads,
                nThreads,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>());
    }

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

}
