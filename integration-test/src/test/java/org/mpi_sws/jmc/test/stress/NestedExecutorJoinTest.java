package org.mpi_sws.jmc.test.stress;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Test that mimics Iceberg's pattern where:
 * 1. Main thread submits tasks to an executor
 * 2. Those tasks internally use another executor (like Iceberg's ThreadPools.getWorkerPool())
 * 3. Main thread waits on futures
 *
 * This should reproduce the scheduler livelock where Task 1 gets stuck in yield/resume loop.
 */
public class NestedExecutorJoinTest {

    // Shared worker pool (like Iceberg's ThreadPools.getWorkerPool())


    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testNestedExecutorPattern() throws Exception {
        final ExecutorService WORKER_POOL = Executors.newFixedThreadPool(2);

        // Main executor (like the one created in the test)
        ExecutorService mainExecutor = Executors.newFixedThreadPool(2);
        List<Future<Integer>> futures = new ArrayList<>();

        // Submit 3 tasks that each use the worker pool internally
        for (int i = 0; i < 3; i++) {
            final int taskId = i;
            futures.add(mainExecutor.submit(() -> {

                // Each task submits work to the shared worker pool
                List<Future<String>> workerFutures = new ArrayList<>();
                for (int j = 0; j < 2; j++) {
                    final int workerId = j;
                    workerFutures.add(WORKER_POOL.submit(() -> {
                        return "result-" + taskId + "-" + workerId;
                    }));
                }

                // Wait for worker tasks
                for (Future<String> wf : workerFutures) {
                    wf.get();
                }

                return taskId;
            }));
        }

        // Main thread waits for all tasks
        for (int i = 0; i < futures.size(); i++) {
            Integer result = futures.get(i).get();
        }

        mainExecutor.shutdown();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false, strategy = "pct", timeout = 10000L)
    public void testNestedExecutorPatternPct() throws Exception {
        final ExecutorService WORKER_POOL = Executors.newFixedThreadPool(2);

        // Main executor (like the one created in the test)
        ExecutorService mainExecutor = Executors.newFixedThreadPool(2);
        List<Future<Integer>> futures = new ArrayList<>();

        // Submit 3 tasks that each use the worker pool internally
        for (int i = 0; i < 3; i++) {
            final int taskId = i;
            futures.add(mainExecutor.submit(() -> {

                // Each task submits work to the shared worker pool
                List<Future<String>> workerFutures = new ArrayList<>();
                for (int j = 0; j < 2; j++) {
                    final int workerId = j;
                    workerFutures.add(WORKER_POOL.submit(() -> {
                        return "result-" + taskId + "-" + workerId;
                    }));
                }

                // Wait for worker tasks
                for (Future<String> wf : workerFutures) {
                    wf.get();
                }

                return taskId;
            }));
        }

        // Main thread waits for all tasks
        for (int i = 0; i < futures.size(); i++) {
            Integer result = futures.get(i).get();
        }

        mainExecutor.shutdown();
    }
}
