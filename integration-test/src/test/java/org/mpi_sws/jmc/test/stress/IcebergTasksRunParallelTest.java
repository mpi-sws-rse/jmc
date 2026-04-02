package org.mpi_sws.jmc.test.stress;

import org.junit.jupiter.api.Disabled;
import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress tests for Iceberg's Tasks.runParallel method.
 *
 * These tests focus on the parallel execution logic in Tasks.Builder.runParallel(),
 * which uses ExecutorService to run tasks concurrently with support for:
 * - Failure handling (stopOnFailure, onFailure callbacks)
 * - Abort tasks (abortTask, stopAbortsOnFailure)
 * - Revert tasks (revertTask, stopRevertsOnFailure)
 * - Retry logic with exponential backoff
 * - AtomicBoolean flags for coordination (taskFailed, abortFailed, revertFailed)
 * - Future.get() calls for synchronization
 *
 * Potential race conditions:
 * 1. Multiple threads updating AtomicBoolean flags simultaneously
 * 2. Future.get() visibility issues (similar to JDK-8358601)
 * 3. Race between task execution and abort logic
 * 4. Race between failure detection and revert logic
 * 5. Concurrent access to succeeded queue
 * 6. Retry logic races with failure flags
 */
public class IcebergTasksRunParallelTest {

    /**
     * Simulates Iceberg's Tasks.Builder pattern for testing.
     * This is a simplified version focusing on the parallel execution logic.
     */
    static class TasksBuilder<I> {
        private final Iterable<I> items;
        private ExecutorService service;
        private FailureTask<I> onFailure;
        private boolean stopOnFailure = false;
        private boolean throwFailureWhenFinished = true;
        private Task<I> revertTask;
        private boolean stopRevertsOnFailure = false;
        private Task<I> abortTask;
        private boolean stopAbortsOnFailure = false;
        private int maxAttempts = 1;

        public TasksBuilder(Iterable<I> items) {
            this.items = items;
        }

        public TasksBuilder<I> executeWith(ExecutorService svc) {
            this.service = svc;
            return this;
        }

        public TasksBuilder<I> onFailure(FailureTask<I> task) {
            this.onFailure = task;
            return this;
        }

        public TasksBuilder<I> stopOnFailure() {
            this.stopOnFailure = true;
            return this;
        }

        public TasksBuilder<I> throwFailureWhenFinished(boolean throwWhenFinished) {
            this.throwFailureWhenFinished = throwWhenFinished;
            return this;
        }

        public TasksBuilder<I> revertWith(Task<I> task) {
            this.revertTask = task;
            return this;
        }

        public TasksBuilder<I> stopRevertsOnFailure() {
            this.stopRevertsOnFailure = true;
            return this;
        }

        public TasksBuilder<I> abortWith(Task<I> task) {
            this.abortTask = task;
            return this;
        }

        public TasksBuilder<I> stopAbortsOnFailure() {
            this.stopAbortsOnFailure = true;
            return this;
        }

        public TasksBuilder<I> retry(int nTimes) {
            this.maxAttempts = nTimes + 1;
            return this;
        }

        public boolean run(Task<I> task) {
            return runParallel(task);
        }

        /**
         * Core parallel execution logic - this is what we're stress testing.
         * Mirrors the implementation in Iceberg's Tasks.java.
         */
        private boolean runParallel(final Task<I> task) {
            final Queue<I> succeeded = new ConcurrentLinkedQueue<>();
            final Queue<Throwable> exceptions = new ConcurrentLinkedQueue<>();
            final AtomicBoolean taskFailed = new AtomicBoolean(false);
            final AtomicBoolean abortFailed = new AtomicBoolean(false);
            final AtomicBoolean revertFailed = new AtomicBoolean(false);

            List<Future<?>> futures = new ArrayList<>();

            // Submit tasks
            for (final I item : items) {
                futures.add(service.submit(() -> {
                    if (!(stopOnFailure && taskFailed.get())) {
                        // Run the task
                        boolean threw = true;
                        try {
                            runTaskWithRetry(task, item);
                            succeeded.add(item);
                            threw = false;
                        } catch (Exception e) {
                            taskFailed.set(true);
                            exceptions.add(e);
                            if (onFailure != null) {
                                tryRunOnFailure(item, e);
                            }
                        } finally {
                            if (threw) {
                                taskFailed.set(true);
                            }
                        }
                    } else if (abortTask != null) {
                        // Abort the task
                        if (stopAbortsOnFailure && abortFailed.get()) {
                            return;
                        }
                        boolean failed = true;
                        try {
                            abortTask.run(item);
                            failed = false;
                        } catch (Exception e) {
                            // Swallow exception
                        } finally {
                            if (failed) {
                                abortFailed.set(true);
                            }
                        }
                    }
                }));
            }

            // Wait for all tasks
            waitFor(futures);
            futures.clear();

            // Revert if needed
            if (taskFailed.get() && revertTask != null) {
                for (final I item : succeeded) {
                    futures.add(service.submit(() -> {
                        if (stopRevertsOnFailure && revertFailed.get()) {
                            return;
                        }
                        boolean failed = true;
                        try {
                            revertTask.run(item);
                            failed = false;
                        } catch (Exception e) {
                            // Swallow exception
                        } finally {
                            if (failed) {
                                revertFailed.set(true);
                            }
                        }
                    }));
                }
                waitFor(futures);
            }

            if (throwFailureWhenFinished && !exceptions.isEmpty()) {
                throw new RuntimeException("Task failed: " + exceptions.iterator().next().getMessage());
            } else if (throwFailureWhenFinished && taskFailed.get()) {
                throw new RuntimeException("Task set failed with an uncaught throwable");
            }

            return !taskFailed.get();
        }

        private void runTaskWithRetry(Task<I> task, I item) throws Exception {
            int attempt = 0;
            while (true) {
                attempt++;
                try {
                    task.run(item);
                    break;
                } catch (Exception e) {
                    if (attempt >= maxAttempts) {
                        throw e;
                    }
                    // Simple retry without backoff for testing
                }
            }
        }

        private void tryRunOnFailure(I item, Exception failure) {
            try {
                onFailure.run(item, failure);
            } catch (Exception e) {
                // Swallow exception
            }
        }

        private void waitFor(List<Future<?>> futures) {
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } catch (ExecutionException | CancellationException e) {
                    // Ignore - exceptions are tracked separately
                }
            }
        }
    }

    interface Task<I> {
        void run(I item) throws Exception;
    }

    interface FailureTask<I> {
        void run(I item, Exception exception);
    }

    /**
     * Test 1: Basic parallel execution with multiple tasks.
     * Tests the core parallel execution without failures.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
    public void testBasicParallelExecution() {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        AtomicInteger counter = new AtomicInteger(0);
        List<Integer> items = List.of(1, 2, 3, 4, 5);

        try {
            boolean result = new TasksBuilder<>(items)
                    .executeWith(executor)
                    .throwFailureWhenFinished(false)
                    .run(item -> {
                        counter.incrementAndGet();
                    });

            assertTrue(result);
            assertEquals(5, counter.get());
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Test 2: Parallel execution with task failures and stopOnFailure.
     * Tests the taskFailed AtomicBoolean flag coordination.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testParallelExecutionWithFailures() {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        ConcurrentLinkedQueue<Integer> executed = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Integer> failed = new ConcurrentLinkedQueue<>();
        List<Integer> items = List.of(1, 2, 3, 4, 5);

        try {
            boolean result = new TasksBuilder<>(items)
                    .executeWith(executor)
                    .stopOnFailure()
                    .throwFailureWhenFinished(false)
                    .onFailure((item, exception) -> {
                        failed.add(item);
                    })
                    .run(item -> {
                        executed.add(item);
                        if (item == 2) {
                            throw new RuntimeException("Task 2 fails");
                        }
                    });

            assertFalse(result);
            assertTrue(failed.contains(2));
            assertTrue(executed.contains(1) || executed.contains(2));
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Test 3: Abort mechanism with concurrent task execution.
     * Tests the race between task execution and abort logic.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testAbortMechanismRace() {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        ConcurrentLinkedQueue<Integer> executed = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Integer> aborted = new ConcurrentLinkedQueue<>();
        List<Integer> items = List.of(1, 2, 3, 4, 5);

        try {
            new TasksBuilder<>(items)
                    .executeWith(executor)
                    .stopOnFailure()
                    .throwFailureWhenFinished(false)
                    .abortWith(item -> {
                        aborted.add(item);
                    })
                    .run(item -> {
                        executed.add(item);
                        if (item == 1) {
                            throw new RuntimeException("Task 1 fails");
                        }
                    });

            // Verify no task is both executed and aborted
            for (Integer item : executed) {
                assertFalse(aborted.contains(item),
                        "Item " + item + " was both executed and aborted");
            }
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Test 4: Revert mechanism with concurrent failures.
     * Tests the race between success tracking and revert logic.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testRevertMechanismRace() {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        ConcurrentLinkedQueue<Integer> executed = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Integer> reverted = new ConcurrentLinkedQueue<>();
        List<Integer> items = List.of(1, 2, 3, 4, 5);

        try {
            new TasksBuilder<>(items)
                    .executeWith(executor)
                    .stopOnFailure()
                    .throwFailureWhenFinished(false)
                    .revertWith(item -> {
                        reverted.add(item);
                    })
                    .run(item -> {
                        executed.add(item);
                        if (item >= 4) {
                            throw new RuntimeException("Task " + item + " fails");
                        }
                    });

            // Only successful tasks should be reverted
            for (Integer item : reverted) {
                assertTrue(executed.contains(item),
                        "Reverted item " + item + " was never executed");
            }
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Test 5: Multiple concurrent failures with onFailure callback.
     * Tests concurrent access to the exceptions queue and onFailure execution.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testMultipleConcurrentFailures() {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        ConcurrentLinkedQueue<Integer> failedItems = new ConcurrentLinkedQueue<>();
        List<Integer> items = List.of(1, 2, 3, 4, 5);

        try {
            new TasksBuilder<>(items)
                    .executeWith(executor)
                    .throwFailureWhenFinished(false)
                    .onFailure((item, exception) -> {
                        failedItems.add(item);
                    })
                    .run(item -> {
                        if (item % 2 == 0) {
                            throw new RuntimeException("Even item fails");
                        }
                    });

            // All even items should have failed
            assertTrue(failedItems.contains(2));
            assertTrue(failedItems.contains(4));
            assertFalse(failedItems.contains(1));
            assertFalse(failedItems.contains(3));
            assertFalse(failedItems.contains(5));
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Test 6: Retry mechanism with concurrent task execution.
     * Tests retry logic races with the taskFailed flag.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testRetryMechanismWithParallelExecution() {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        ConcurrentHashMap<Integer, AtomicInteger> attemptCounts = new ConcurrentHashMap<>();
        List<Integer> items = List.of(1, 2, 3);

        for (int i : items) {
            attemptCounts.put(i, new AtomicInteger(0));
        }

        try {
            boolean result = new TasksBuilder<>(items)
                    .executeWith(executor)
                    .retry(2)
                    .throwFailureWhenFinished(false)
                    .run(item -> {
                        int attempt = attemptCounts.get(item).incrementAndGet();
                        if (item == 2 && attempt < 2) {
                            throw new RuntimeException("Task 2 fails on attempt " + attempt);
                        }
                    });

            assertTrue(result);
            assertEquals(1, attemptCounts.get(1).get());
            assertEquals(2, attemptCounts.get(2).get());
            assertEquals(1, attemptCounts.get(3).get());
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Test 7: stopAbortsOnFailure flag coordination.
     * Tests the abortFailed AtomicBoolean flag with concurrent aborts.
     *  TODO This needs JmcAtomicBoolean.getAndSet(boolean), currently throws NoSuchMEthodException
     */
    @Disabled
    public void testStopAbortsOnFailureFlag() {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        ConcurrentLinkedQueue<Integer> aborted = new ConcurrentLinkedQueue<>();
        AtomicBoolean firstAbortFailed = new AtomicBoolean(false);
        List<Integer> items = List.of(1, 2, 3, 4, 5);

        try {
            new TasksBuilder<>(items)
                    .executeWith(executor)
                    .stopOnFailure()
                    .stopAbortsOnFailure()
                    .throwFailureWhenFinished(false)
                    .abortWith(item -> {
                        aborted.add(item);
                        if (item == 3 && !firstAbortFailed.getAndSet(true)) {
                            throw new RuntimeException("Abort fails for item 3");
                        }
                    })
                    .run(item -> {
                        if (item == 1) {
                            throw new RuntimeException("Task 1 fails");
                        }
                    });

            // With stopAbortsOnFailure, aborts should stop after first failure
            assertTrue(aborted.size() <= items.size());
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Test 8: stopRevertsOnFailure flag coordination.
     * Tests the revertFailed AtomicBoolean flag with concurrent reverts.
     *  TODO This needs JmcAtomicBoolean.getAndSet(boolean), currently throws NoSuchMEthodException
     */
    @Disabled
    public void testStopRevertsOnFailureFlag() {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        ConcurrentLinkedQueue<Integer> reverted = new ConcurrentLinkedQueue<>();
        AtomicBoolean firstRevertFailed = new AtomicBoolean(false);
        List<Integer> items = List.of(1, 2, 3, 4, 5);

        try {
            new TasksBuilder<>(items)
                    .executeWith(executor)
                    .stopOnFailure()
                    .stopRevertsOnFailure()
                    .throwFailureWhenFinished(false)
                    .revertWith(item -> {
                        reverted.add(item);
                        if (item == 2 && !firstRevertFailed.getAndSet(true)) {
                            throw new RuntimeException("Revert fails for item 2");
                        }
                    })
                    .run(item -> {
                        if (item >= 4) {
                            throw new RuntimeException("Task " + item + " fails");
                        }
                    });

            // With stopRevertsOnFailure, reverts should stop after first failure
            assertTrue(reverted.size() <= 3);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Test 9: Future.get() visibility with concurrent task completion.
     * Tests for potential visibility issues similar to JDK-8358601.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testFutureGetVisibility() {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        ConcurrentLinkedQueue<Integer> completed = new ConcurrentLinkedQueue<>();
        List<Integer> items = List.of(1, 2, 3, 4, 5);

        try {
            boolean result = new TasksBuilder<>(items)
                    .executeWith(executor)
                    .throwFailureWhenFinished(false)
                    .run(item -> {
                        completed.add(item);
                    });

            assertTrue(result);
            // All items should be visible after Future.get() returns
            assertEquals(5, completed.size());
            for (int i = 1; i <= 5; i++) {
                assertTrue(completed.contains(i), "Item " + i + " not found in completed queue");
            }
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Test 10: Complex scenario with all features enabled.
     * Tests interaction between all mechanisms: failures, retries, aborts, reverts.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testComplexScenarioAllFeatures() {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        ConcurrentLinkedQueue<Integer> executed = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Integer> failed = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Integer> aborted = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Integer> reverted = new ConcurrentLinkedQueue<>();
        ConcurrentHashMap<Integer, AtomicInteger> attempts = new ConcurrentHashMap<>();
        List<Integer> items = List.of(1, 2, 3, 4, 5);

        for (int i : items) {
            attempts.put(i, new AtomicInteger(0));
        }

        try {
            new TasksBuilder<>(items)
                    .executeWith(executor)
                    .stopOnFailure()
                    .retry(1)
                    .throwFailureWhenFinished(false)
                    .onFailure((item, exception) -> {
                        failed.add(item);
                    })
                    .abortWith(item -> {
                        aborted.add(item);
                    })
                    .revertWith(item -> {
                        reverted.add(item);
                    })
                    .run(item -> {
                        int attempt = attempts.get(item).incrementAndGet();
                        executed.add(item);

                        // Item 3 fails even after retry
                        if (item == 3) {
                            throw new RuntimeException("Task 3 always fails");
                        }
                    });

            // Verify invariants
            assertTrue(failed.contains(3), "Task 3 should have failed");

            // No item should be both executed successfully and aborted
            for (Integer item : executed) {
                if (!failed.contains(item)) {
                    assertFalse(aborted.contains(item),
                            "Item " + item + " was both successful and aborted");
                }
            }

            // Only successful tasks should be reverted
            for (Integer item : reverted) {
                assertTrue(executed.contains(item),
                        "Reverted item " + item + " was never executed");
                assertFalse(failed.contains(item),
                        "Reverted item " + item + " was marked as failed");
            }
        } finally {
            executor.shutdown();
        }
    }
}
