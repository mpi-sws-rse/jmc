package org.mpisws.jmc.api.util.concurrent;

import java.util.concurrent.ExecutorService;

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
}
