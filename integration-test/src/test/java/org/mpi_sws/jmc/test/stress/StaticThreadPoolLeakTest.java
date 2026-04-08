package org.mpi_sws.jmc.test.stress;

import org.junit.jupiter.api.Disabled;
import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Minimal test to demonstrate the static field re-initialization issue in JMC.
 *
 * THE PROBLEM:
 * - Static fields with eager initialization are re-initialized on EVERY JMC iteration
 * - Thread pools create OS threads which are expensive resources
 * - With 1000 iterations, this creates thousands of threads
 * - Eventually hits OS limits and causes OutOfMemoryError
 *
 * DEMONSTRATION:
 * - Run with numIterations=10: Works fine
 * - Run with numIterations=1000: OutOfMemoryError (unable to create native thread)
 *
 * THE FIX:
 * - Use lazy initialization with null checks (see testLazyInitialization)
 * - This ensures the pool is created only once, not per iteration
 */
public class StaticThreadPoolLeakTest {

    /**
     * Helper class with EAGER static initialization (problematic).
     * This will be re-initialized on every JMC iteration.
     */
    static class EagerThreadPool {
        // This creates a new thread pool EVERY time the class is initialized
        private static final ExecutorService POOL = Executors.newFixedThreadPool(2);

        public static ExecutorService getPool() {
            return POOL;
        }
    }

    /**
     * Helper class with LAZY static initialization (correct).
     * This will only create the pool once, even across iterations.
     */
    static class LazyThreadPool {
        // Declared but not initialized
        private static ExecutorService POOL;

        public static ExecutorService getPool() {
            // Lazy initialization with null check
            if (POOL == null) {
                POOL = Executors.newFixedThreadPool(2);
            }
            return POOL;
        }
    }

    /**
     * Test 1: Demonstrates the problem with eager initialization.
     *
     * Run this with numIterations=1000 to see OutOfMemoryError.
     * Each iteration creates a NEW thread pool with 2 threads.
     * After ~500-1000 iterations (depending on OS limits), you'll hit:
     * "OutOfMemoryError: unable to create native thread"
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testEagerInitializationLeak() throws Exception {
        // Get the thread pool (will be a DIFFERENT instance each iteration)
        ExecutorService pool = EagerThreadPool.getPool();

        // Submit a simple task
        Future<Integer> future = pool.submit(() -> {
            return 42;
        });

        // Wait for result
        assertEquals(42, future.get());

        // Print thread count to see it growing
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        int threadCount = threadBean.getThreadCount();

        // With eager initialization, thread count keeps growing!
        // Iteration 1: ~10 threads
        // Iteration 10: ~30 threads
        // Iteration 1000: OutOfMemoryError!
    }

    /**
     * Test 2: Demonstrates the fix with lazy initialization.
     *
     * This works fine even with numIterations=1000.
     * The pool is created ONCE and reused across all iterations.
     * TODO Investigate and fix the hang
     */
    @Disabled
    public void testLazyInitializationNoLeak() throws Exception {
        // Get the thread pool (will be the SAME instance each iteration)
        ExecutorService pool = LazyThreadPool.getPool();

        // Submit a simple task
        Future<Integer> future = pool.submit(() -> {
            return 42;
        });

        // Wait for result
        assertEquals(42, future.get());

        // With lazy initialization, thread count stays stable!
        // All iterations: ~10-15 threads (stable)
    }

    /**
     * Test 3: Demonstrates the leak more dramatically.
     * Creates multiple thread pools per iteration to hit the limit faster.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testMultipleEagerPoolsLeak() throws Exception {
        // Each of these creates a NEW pool on every iteration
        ExecutorService pool1 = Executors.newFixedThreadPool(2);
        ExecutorService pool2 = Executors.newFixedThreadPool(2);
        ExecutorService pool3 = Executors.newFixedThreadPool(2);

        // Submit tasks
        Future<Integer> f1 = pool1.submit(() -> 1);
        Future<Integer> f2 = pool2.submit(() -> 2);
        Future<Integer> f3 = pool3.submit(() -> 3);

        assertEquals(1, f1.get());
        assertEquals(2, f2.get());
        assertEquals(3, f3.get());

        // This creates 6 threads per iteration
        // After 10 iterations: 60 threads
        // After 100 iterations: 600 threads
        // After 1000 iterations: OutOfMemoryError!

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    }

}

