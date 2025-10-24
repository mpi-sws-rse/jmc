package org.mpi_sws.jmc.api.util.concurrent;

import java.util.concurrent.ExecutorService;
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
        return new JmcThreadPoolExecutor(1);
    }

    /**
     * Creates a fixed thread pool with the specified number of threads that uses a JMC executor
     * service.
     *
     * @param nThreads the number of threads in the pool
     * @return a new fixed thread pool executor
     */
    public static ExecutorService newFixedThreadPool(int nThreads) {
        return new JmcThreadPoolExecutor(nThreads);
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
        return new JmcThreadPoolExecutor(nThreads);
    }

    public static ExecutorService newSingleThreadExecutor(ThreadFactory threadFactory) {
        return new JmcThreadPoolExecutor(1);
    }
}
