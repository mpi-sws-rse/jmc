package org.mpi_sws.jmc.test.stress;

import org.junit.jupiter.api.Disabled;
import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stress test to reproduce the race condition in JmcExecutorWorker
 * where multiple tasks completing simultaneously cause incorrect
 * join() vs terminate() decisions due to racy queue.isEmpty() check.
 */
// TODO :: These tests do not explicitly call future, JMC cannot
//  support them currently, we need to extend JMC to support them.
public class ExecutorWithoutFutureTest {

    @Disabled
    public  void testMultipleTasksCompletingSimultaneously() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(7);
        AtomicInteger counter = new AtomicInteger(0);

        // Submit 7 tasks that do minimal work
        // This maximizes the chance they'll all complete around the same time
        // and all see queue.isEmpty() == true
        for (int i = 0; i < 7; i++) {
            executor.submit(() -> {
                counter.incrementAndGet();
                return null;
            });
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


    @Disabled
    public void testManyTasksWithShutdown() throws InterruptedException {
        // Even more tasks to increase race condition probability
        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger counter = new AtomicInteger(0);

        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                counter.incrementAndGet();
                return null;
            });
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


    @Disabled
    public  void testRaceConditionWithDebug() throws InterruptedException {
        // Smaller test with debug enabled to see the exact error sequence
        ExecutorService executor = Executors.newFixedThreadPool(5);
        AtomicInteger counter = new AtomicInteger(0);

        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                counter.incrementAndGet();
                return null;
            });
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


    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, strategy = "pct", timeout = 10000L)
    @Disabled
    public void testMultipleTasksCompletingSimultaneouslyPct() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(7);
        AtomicInteger counter = new AtomicInteger(0);

        // Submit 7 tasks that do minimal work
        // This maximizes the chance they'll all complete around the same time
        // and all see queue.isEmpty() == true
        for (int i = 0; i < 7; i++) {
            executor.submit(() -> {
                counter.incrementAndGet();
                return null;
            });
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
    @JmcCheckConfiguration(numIterations = 10, strategy = "pct", timeout = 10000L)
    @Disabled
    public void testManyTasksWithShutdownPct() throws InterruptedException {
        // Even more tasks to increase race condition probability
        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger counter = new AtomicInteger(0);

        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                counter.incrementAndGet();
                return null;
            });
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
    @JmcCheckConfiguration(numIterations = 10, strategy = "pct", timeout = 10000L)
    @Disabled
    public void testRaceConditionWithDebugPct() throws InterruptedException {
        // Smaller test with debug enabled to see the exact error sequence
        ExecutorService executor = Executors.newFixedThreadPool(5);
        AtomicInteger counter = new AtomicInteger(0);

        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                counter.incrementAndGet();
                return null;
            });
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
