package org.mpi_sws.jmc.test.stress;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * Stress test that reproduces the Iceberg busy-wait scenario.
 * <p>
 * This test simulates:
 * 1. A main thread that busy-waits on futures using isDone()
 * 2. Worker threads that do heavy field access work
 * <p>
 * This pattern causes performance issues because:
 * - Every isDone() call reads CompletableFuture.result field
 * - Every field read is instrumented and causes a yield
 * - The busy-wait creates thousands of unnecessary scheduling points
 */
public class BusyWaitFutureTest {

    /**
     * A simple class with fields to simulate heavy field access work
     */
    static class DataProcessor {
        private int field1;
        private int field2;
        private int field3;
        private String field4;
        private Object field5;

        public void processData() {
            // Simulate work with multiple field accesses
            for (int i = 0; i < 100; i++) {
                field1 = i;
                field2 = field1 * 2;
                field3 = field1 + field2;
                field4 = "data_" + field3;
                field5 = new Object();

                // Read fields
                int sum = field1 + field2 + field3;
                String str = field4;
                Object obj = field5;
            }
        }
    }

    /**
     * Busy-wait implementation (like Iceberg's Tasks.waitFor)
     */
    private static void busyWaitForFutures(Collection<Future<?>> futures) {
        while (true) {
            int numFinished = 0;
            for (Future<?> future : futures) {
                if (future.isDone()) {  // This causes repeated field reads
                    numFinished += 1;
                }
            }

            if (numFinished == futures.size()) {
                // All done, collect results
                for (Future<?> future : futures) {
                    try {
                        future.get();
                    } catch (Exception e) {
                        // Ignore for this test
                    }
                }
                return;
            } else {
                try {
                    Thread.sleep(1);  // Sleep and retry
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * Blocking implementation (proposed fix)
     */
    private static void blockingWaitForFutures(Collection<Future<?>> futures) {
        // Wait for all futures to complete by blocking
        for (Future<?> future : futures) {
            try {
                future.get();  // Block until complete
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (ExecutionException | CancellationException e) {
                // Ignore exceptions in first pass
            }
        }
    }

    /**
     * Test with busy-wait (expected to be slow/hang)
     */
    public static void testBusyWait() throws Exception {
        ExecutorService service = Executors.newFixedThreadPool(2);
        List<Future<?>> futures = new ArrayList<>();

        // Submit tasks that do heavy field access work
        for (int i = 0; i < 2; i++) {
            Future<?> future = service.submit(() -> {
                DataProcessor processor = new DataProcessor();
                processor.processData();
                return null;
            });
            futures.add(future);
        }

        // Busy-wait for completion
        busyWaitForFutures(futures);
    }

    /**
     * Test with blocking wait (expected to work efficiently)
     */
    public static void testBlockingWait() throws Exception {
        ExecutorService service = Executors.newFixedThreadPool(2);
        List<Future<?>> futures = new ArrayList<>();

        // Submit tasks that do heavy field access work
        for (int i = 0; i < 2; i++) {
            Future<?> future = service.submit(() -> {
                DataProcessor processor = new DataProcessor();
                processor.processData();
                return null;
            });
            futures.add(future);
        }

        // Block for completion
        blockingWaitForFutures(futures);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 3)
    public void testBusyWaitScenario() throws Exception {
        testBusyWait();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 3)
    public void testBlockingWaitScenario() throws Exception {
        testBlockingWait();
    }
}
