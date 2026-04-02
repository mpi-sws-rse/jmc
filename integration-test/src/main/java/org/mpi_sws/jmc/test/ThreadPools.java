package org.mpi_sws.jmc.test;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class ThreadPools {

    /**
     * Simulates Iceberg's ThreadPools utility for testing.
     */
        private static final int WORKER_THREAD_POOL_SIZE = 4;
        private static final int DELETE_WORKER_THREAD_POOL_SIZE = 2;

        private static final ExecutorService WORKER_POOL =
                Executors.newFixedThreadPool(WORKER_THREAD_POOL_SIZE);

        private static final ExecutorService DELETE_WORKER_POOL =
                Executors.newFixedThreadPool(DELETE_WORKER_THREAD_POOL_SIZE);

        private static final ScheduledExecutorService AUTH_REFRESH_POOL =
                Executors.newScheduledThreadPool(1);

        public static ExecutorService getWorkerPool() {
            return WORKER_POOL;
        }

        public static ExecutorService getDeleteWorkerPool() {
            return DELETE_WORKER_POOL;
        }

        public static ScheduledExecutorService authRefreshPool() {
            return AUTH_REFRESH_POOL;
        }

    /**
     * Creates a fixed-size thread pool that uses daemon threads.
     * Simplified version without MoreExecutors.getExitingExecutorService for testing.
     */
    public static ExecutorService newExitingWorkerPool(String namePrefix, int poolSize) {
        // In tests, we skip the exiting wrapper to avoid shutdown hook registration
        return newFixedThreadPool(namePrefix, poolSize);
    }

        public static ExecutorService newFixedThreadPool(String namePrefix, int poolSize) {
            return Executors.newFixedThreadPool(poolSize);
        }

        public static ScheduledExecutorService newScheduledPool(String namePrefix, int poolSize) {
            return Executors.newScheduledThreadPool(poolSize);
        }

    /**
     * Create a new ScheduledExecutorService with the given name and pool size.
     * The service registers a shutdown hook to ensure that it terminates when the JVM exits.
     * Simplified version without MoreExecutors.getExitingScheduledExecutorService for testing.
     */
    public static ScheduledExecutorService newExitingScheduledPool(
            String namePrefix, int poolSize, Duration terminationTimeout) {
        // In tests, we skip the exiting wrapper to avoid shutdown hook registration
        return newScheduledPool(namePrefix, poolSize);
    }

    public static void shutdownAll() {
        WORKER_POOL.shutdown();
        DELETE_WORKER_POOL.shutdown();
        AUTH_REFRESH_POOL.shutdown();
    }
}

