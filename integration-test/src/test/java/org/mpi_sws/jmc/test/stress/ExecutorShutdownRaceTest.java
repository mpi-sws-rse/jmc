package org.mpi_sws.jmc.test.stress;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.api.util.concurrent.JmcThread;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stress test to reproduce the race condition in JmcExecutorWorker
 * where multiple tasks completing simultaneously cause incorrect
 * join() vs terminate() decisions due to racy queue.isEmpty() check.
 */
public class ExecutorShutdownRaceTest {

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
    public void testMultipleTasksCompletingSimultaneously() {
        ExecutorService executor = Executors.newFixedThreadPool(7);
        AtomicInteger counter = new AtomicInteger(0);

        // Submit 7 tasks that do minimal work
        // This maximizes the chance they'll all complete around the same time
        // and all see queue.isEmpty() == true
        List<Future> futures = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            Future f = executor.submit(() -> {
                counter.incrementAndGet();
                return null;
            });
            futures.add(f);
        }

        for (Future f : futures) {
            try {
                f.get();
            } catch (InterruptedException e) {

            } catch (ExecutionException e) {

            }
        }
        executor.shutdown();
        //boolean terminated = executor.awaitTermination(1, TimeUnit.MINUTES);

//        if (!terminated) {
//            throw new RuntimeException("Executor did not terminate in time");
//        }

        if (counter.get() != 7) {
            throw new RuntimeException("Expected 7 tasks to complete, but got " + counter.get());
        }
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
    public void testManyTasksWithShutdown() throws InterruptedException {
        // Even more tasks to increase race condition probability
        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger counter = new AtomicInteger(0);

        List<Future> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Future f = executor.submit(() -> {
                counter.incrementAndGet();
                return null;
            });
            futures.add(f);
        }

        for (Future f : futures) {
            try {
                f.get();
            } catch (InterruptedException e) {
            } catch (ExecutionException e) {
            }
        }

        executor.shutdown();
        boolean terminated = executor.awaitTermination(1, TimeUnit.MINUTES);

        if (!terminated) {
            throw new RuntimeException("Executor did not terminate in time");
        }

        if (counter.get() != 10) {
            throw new RuntimeException("Expected 10 tasks to complete, but got " + counter.get());
        }
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 5)
    public void testRaceConditionWithDebug() throws InterruptedException {
        // Smaller test with debug enabled to see the exact error sequence
        ExecutorService executor = Executors.newFixedThreadPool(5);
        AtomicInteger counter = new AtomicInteger(0);

        List<Future> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Future f = executor.submit(() -> {
                counter.incrementAndGet();
                return null;
            });
            futures.add(f);
        }

        for (Future f : futures) {
            try {
                f.get();
            } catch (InterruptedException e) {
            } catch (ExecutionException e) {
            }
        }

        executor.shutdown();
        boolean terminated = executor.awaitTermination(1, TimeUnit.MINUTES);

        if (!terminated) {
            throw new RuntimeException("Executor did not terminate in time");
        }

        if (counter.get() != 5) {
            throw new RuntimeException("Expected 5 tasks to complete, but got " + counter.get());
        }
    }
}
