package org.mpi_sws.jmc.test.stress;

import org.junit.jupiter.api.Disabled;
import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.annotations.JmcExpectAssertionFailure;
import org.mpi_sws.jmc.test.ThreadPools;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress tests for Iceberg's ThreadPools utility class.
 *
 * These tests focus on the thread pool management and concurrent access patterns:
 * - Shared singleton pools (WORKER_POOL, DELETE_WORKER_POOL, authRefreshPool)
 * - Thread pool creation methods (newFixedThreadPool, newScheduledPool)
 * - Concurrent task submission to shared pools
 * - ScheduledExecutorService timing and coordination
 * - Thread factory and daemon thread behavior
 *
 * Potential race conditions:
 * 1. Concurrent access to singleton pools from multiple threads
 * 2. Task submission races with pool shutdown
 * 3. ScheduledExecutorService timing races
 * 4. Future.get() visibility issues
 * 5. Concurrent task completion and result collection
 */
public class IcebergThreadPoolsTest {



    /**
     * Test 1: Concurrent access to shared worker pool.
     * Tests thread-safety of getWorkerPool() and concurrent task submission.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testConcurrentWorkerPoolAccess() {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        ExecutorService pool = ThreadPools.getWorkerPool();
        AtomicInteger counter = new AtomicInteger(0);
        List<Future<Integer>> futures = new ArrayList<>();

        // Submit tasks from multiple threads concurrently
        for (int i = 0; i < 10; i++) {
            futures.add(pool.submit(() -> {
                //System.out.println("Thread: " + Thread.currentThread().getName() + " - inside submit");
                return counter.incrementAndGet();
            }));
        }

        // Wait for all tasks
        int sum = 0;
        for (Future<Integer> future : futures) {
            try {
                sum += future.get();
            } catch (Exception e) {
                fail("Task execution failed: " + e.getMessage());
            }
        }
       //ThreadPools.shutdownAll();

        //assert(sum > 0);
        assertEquals(10, counter.get());
        assertTrue(sum > 0);
    }

    /**
     * Test 2: Concurrent access to shared delete worker pool.
     * Tests thread-safety of getDeleteWorkerPool() with concurrent operations.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testConcurrentDeleteWorkerPoolAccess() {
        ExecutorService pool = ThreadPools.getDeleteWorkerPool();
        ConcurrentLinkedQueue<Integer> results = new ConcurrentLinkedQueue<>();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            final int value = i;
            futures.add(pool.submit(() -> {
                results.add(value);
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                fail("Task execution failed: " + e.getMessage());
            }
        }

        assertEquals(8, results.size());
    }

    /**
     * Test 3: ScheduledExecutorService with concurrent scheduling.
     * Tests authRefreshPool() with multiple scheduled tasks.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testScheduledPoolConcurrentScheduling() {
        ScheduledExecutorService pool = ThreadPools.authRefreshPool();
        AtomicInteger counter = new AtomicInteger(0);
        List<ScheduledFuture<?>> futures = new ArrayList<>();

        // Schedule multiple tasks with minimal delay
        for (int i = 0; i < 5; i++) {
            futures.add(pool.schedule(() -> {
                counter.incrementAndGet();
            }, 1, TimeUnit.MILLISECONDS));
        }

        // Wait for all scheduled tasks
        for (ScheduledFuture<?> future : futures) {
            try {
                future.get(100, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                fail("Scheduled task failed: " + e.getMessage());
            }
        }
        pool.shutdown();

        assertEquals(5, counter.get());
    }

    /**
     * Test 4: Multiple threads creating new thread pools concurrently.
     * Tests newFixedThreadPool() thread-safety.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testConcurrentPoolCreation() throws Exception {
        ConcurrentLinkedQueue<ExecutorService> pools = new ConcurrentLinkedQueue<>();
        List<Future<?>> futures = new ArrayList<>();
        ExecutorService coordinator = Executors.newFixedThreadPool(3);

        try {
            for (int i = 0; i < 3; i++) {
                futures.add(coordinator.submit(() -> {
                    ExecutorService pool = ThreadPools.newFixedThreadPool("test-pool", 2);
                    pools.add(pool);
                }));
            }

            for (Future<?> future : futures) {
                future.get();
            }

            assertEquals(3, pools.size());

            // Cleanup
            for (ExecutorService pool : pools) {
                pool.shutdown();
            }
        } finally {
            coordinator.shutdown();
        }
    }

    /**
     * Test 5: Concurrent task submission with Future.get() synchronization.
     * Tests visibility of task results across threads.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testFutureGetVisibility() {
        ExecutorService pool = ThreadPools.getWorkerPool();
        ConcurrentHashMap<Integer, Integer> results = new ConcurrentHashMap<>();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            final int key = i;
            futures.add(pool.submit(() -> {
                results.put(key, key * 2);
            }));
        }

        // Wait for all tasks
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                fail("Task failed: " + e.getMessage());
            }
        }

        // Verify all results are visible
        assertEquals(10, results.size());
        for (int i = 0; i < 10; i++) {
            assertTrue(results.containsKey(i), "Missing key: " + i);
            assertEquals(i * 2, results.get(i));
        }
    }

    /**
     * Test 6: Scheduled tasks with fixed delay.
     * Tests timing coordination in ScheduledExecutorService.
     * TODO: Fix this test
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    @JmcExpectAssertionFailure
    @Disabled
    public void testScheduledFixedDelay() {
        ScheduledExecutorService pool = ThreadPools.newScheduledPool("test-scheduled", 2);
        AtomicInteger counter = new AtomicInteger(0);
        AtomicBoolean running = new AtomicBoolean(true);

        try {
            ScheduledFuture<?> future = pool.scheduleWithFixedDelay(() -> {
                if (running.get()) {
                    counter.incrementAndGet();
                }
            }, 0, 1, TimeUnit.MILLISECONDS);

            // Let it run briefly
            Thread.sleep(5);
            running.set(false);
            future.cancel(false);

            assertTrue(counter.get() > 0, "Scheduled task should have run at least once");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Test interrupted");
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Test 7: Concurrent task submission to multiple pools.
     * Tests interaction between worker pool and delete worker pool.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testMultiplePoolsConcurrentAccess() {
        ExecutorService workerPool = ThreadPools.getWorkerPool();
        ExecutorService deletePool = ThreadPools.getDeleteWorkerPool();

        AtomicInteger workerCounter = new AtomicInteger(0);
        AtomicInteger deleteCounter = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();

        // Submit to worker pool
        for (int i = 0; i < 5; i++) {
            futures.add(workerPool.submit(() -> {
                workerCounter.incrementAndGet();
            }));
        }

        // Submit to delete pool
        for (int i = 0; i < 5; i++) {
            futures.add(deletePool.submit(() -> {
                deleteCounter.incrementAndGet();
            }));
        }

        // Wait for all
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                fail("Task failed: " + e.getMessage());
            }
        }

        assertEquals(5, workerCounter.get());
        assertEquals(5, deleteCounter.get());
    }

    /**
     * Test 8: Exception handling in concurrent tasks.
     * Tests that exceptions in one task don't affect others.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testConcurrentExceptionHandling() {
        ExecutorService pool = ThreadPools.getWorkerPool();
        AtomicInteger successCount = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            final int value = i;
            futures.add(pool.submit(() -> {
                if (value == 5) {
                    throw new RuntimeException("Task 5 fails");
                }
                successCount.incrementAndGet();
            }));
        }

        int exceptions = 0;
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                exceptions++;
            } catch (Exception e) {
                fail("Unexpected exception: " + e.getMessage());
            }
        }

        assertEquals(1, exceptions);
        assertEquals(9, successCount.get());
    }

    /**
     * Test 9: Callable tasks with return values.
     * Tests concurrent Callable execution and result collection.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testCallableTasksWithResults() {
        ExecutorService pool = ThreadPools.getWorkerPool();
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            final int value = i;
            futures.add(pool.submit(() -> value * value));
        }

        List<Integer> results = new ArrayList<>();
        for (Future<Integer> future : futures) {
            try {
                results.add(future.get());
            } catch (Exception e) {
                fail("Task failed: " + e.getMessage());
            }
        }

        assertEquals(10, results.size());
        assertTrue(results.contains(0));   // 0*0
        assertTrue(results.contains(1));   // 1*1
        assertTrue(results.contains(81));  // 9*9
    }

    /**
     * Test 10: Scheduled tasks with concurrent cancellation.
     * Tests race between task execution and cancellation.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testScheduledTaskCancellation() {
        ScheduledExecutorService pool = ThreadPools.newScheduledPool("test-cancel", 2);
        AtomicInteger counter = new AtomicInteger(0);

        try {
            List<ScheduledFuture<?>> futures = new ArrayList<>();

            // Schedule multiple tasks
            for (int i = 0; i < 5; i++) {
                futures.add(pool.schedule(() -> {
                    counter.incrementAndGet();
                }, 10, TimeUnit.MILLISECONDS));
            }

            // Cancel some tasks immediately
            futures.get(0).cancel(false);
            futures.get(2).cancel(false);

            // Wait for non-cancelled tasks
            try {
                futures.get(1).get();
                futures.get(3).get();
                futures.get(4).get();
            } catch (CancellationException e) {
                // Expected for cancelled tasks
            }

            // Counter should be less than 5 due to cancellations
            assertTrue(counter.get() <= 5);
            assertTrue(counter.get() >= 3);
        } catch (Exception e) {
            fail("Test failed: " + e.getMessage());
        } finally {
            pool.shutdown();
        }
    }


    /**
     * Test 11: newExitingWorkerPool creates functional thread pools.
     * Tests that newExitingWorkerPool creates pools that can execute tasks correctly.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testNewExitingWorkerPool() {
        ExecutorService pool = ThreadPools.newExitingWorkerPool("test-exiting", 3);
        AtomicInteger counter = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < 10; i++) {
                futures.add(pool.submit(() -> {
                    counter.incrementAndGet();
                }));
            }

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    fail("Task failed: " + e.getMessage());
                }
            }

            assertEquals(10, counter.get());
        } finally {
            pool.shutdown();
        }
    }
}
