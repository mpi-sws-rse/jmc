package org.mpisws.util.concurrent;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A thread pool executor that runs tasks in new threads. The thread creation is wrapped with the
 * {@link JmcThreadFactory} to create {@link JmcThread} instances.
 */
public class JmcThreadPoolExecutor extends ThreadPoolExecutor {

    public JmcThreadPoolExecutor(
            int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime,
            @NotNull TimeUnit unit,
            @NotNull BlockingQueue<Runnable> workQueue) {
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
            @NotNull TimeUnit unit,
            @NotNull BlockingQueue<Runnable> workQueue,
            @NotNull ThreadFactory threadFactory) {
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
            @NotNull TimeUnit unit,
            @NotNull BlockingQueue<Runnable> workQueue,
            @NotNull RejectedExecutionHandler handler) {
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
                new JmcThreadFactory(threadFactory),
                handler);
    }
}
