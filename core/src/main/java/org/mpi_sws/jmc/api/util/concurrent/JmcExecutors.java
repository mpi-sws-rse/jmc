package org.mpi_sws.jmc.api.util.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * A replacement for {@link java.util.concurrent.Executors}. Currently only supports a
 * `newSingleThreadExecutor` and `newFixedThreadPool` methods, which return instances of {@link
 * JmcExecutorService}.
 */
public class JmcExecutors {

    /**
     * Creates a single-threaded executor that uses a JMC executor service.
     *
     * @return a new single-threaded executor
     */
    public static ExecutorService newSingleThreadExecutor() {
        return new JmcExecutorService(1);
    }

    /**
     * Creates a fixed thread pool with the specified number of threads that uses a JMC executor
     * service.
     *
     * @param nThreads the number of threads in the pool
     * @return a new fixed thread pool executor
     */
    public static ExecutorService newFixedThreadPool(int nThreads) {
        return new JmcExecutorService(nThreads);
    }

    /**
     * Minimal overload to match java.util.concurrent.Executors.newSingleThreadExecutor(ThreadFactory).
     * For now we ignore the provided ThreadFactory and delegate to the existing no-arg method.
     * (If thread factory semantics become important to model, we can incorporate it later.)
     */
//        Added for iceberg error : java.util.concurrent.ExecutionException: java.lang.NoSuchMethodError:
//            'java.util.concurrent.ExecutorService
//            org.mpi_sws.jmc.api.util.concurrent.JmcExecutors.newFixedThreadPool(int, java.util.concurrent.ThreadFactory)'

    public static ExecutorService newFixedThreadPool(int nThreads, ThreadFactory threadFactory) {
        return new JmcExecutorService(nThreads, new JmcThreadFactory(threadFactory));
    }

    public static ExecutorService newSingleThreadExecutor(ThreadFactory threadFactory) {
        return new JmcExecutorService(1, new JmcThreadFactory(threadFactory));
    }


    /**
     * Creates a fixed thread pool with the specified name prefix and pool size.
     * This method is used to replace calls to ThreadPools.newExitingWorkerPool().
     * The exiting behavior (shutdown hook) is not needed in JMC's controlled execution environment.
     *
     * @param namePrefix the name prefix for threads (ignored in JMC)
     * @param poolSize the number of threads in the pool
     * @return a new fixed thread pool executor
     */
    public static ExecutorService newExitingWorkerPool(String namePrefix, int poolSize) {
        return new JmcExecutorService(poolSize);
    }


    /**
     * Creates a scheduled thread pool with the specified number of threads.
     * In JMC's controlled execution, scheduling delays are modeled as yield points.
     *
     * @param corePoolSize the number of threads in the pool
     * @return a new scheduled thread pool executor
     */
    public static ScheduledExecutorService newScheduledThreadPool(int corePoolSize) {
        return new JmcScheduledExecutorService(corePoolSize);
    }


    /**
     * Creates a scheduled thread pool with the specified number of threads and thread factory.
     * In JMC's controlled execution, scheduling delays are modeled as yield points.
     *
     * @param corePoolSize the number of threads in the pool
     * @param threadFactory the factory to use when creating new threads
     * @return a new scheduled thread pool executor
     */
    public static ScheduledExecutorService newScheduledThreadPool(int corePoolSize, ThreadFactory threadFactory) {
        return new JmcScheduledExecutorService(corePoolSize, new JmcThreadFactory(threadFactory));
    }

    /**
     * Creates a single-threaded scheduled executor.
     * In JMC's controlled execution, scheduling delays are modeled as yield points.
     *
     * @return a new single-threaded scheduled executor
     */
    public static ScheduledExecutorService newSingleThreadScheduledExecutor() {
        return new JmcScheduledExecutorService(1);
    }

    /**
     * Creates a single-threaded scheduled executor with the specified thread factory.
     * In JMC's controlled execution, scheduling delays are modeled as yield points.
     *
     * @param threadFactory the factory to use when creating new threads
     * @return a new single-threaded scheduled executor
     */
    public static ScheduledExecutorService newSingleThreadScheduledExecutor(ThreadFactory threadFactory) {
        return new JmcScheduledExecutorService(1, new JmcThreadFactory(threadFactory));
    }


}
