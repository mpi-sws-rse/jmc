package org.mpi_sws.jmc.test.stress;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.annotations.JmcExpectAssertionFailure;
import org.mpi_sws.jmc.test.structural.staticinit.StaticPatterns;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * Stress test to reproduce the concurrent static initialization race condition.
 *
 * THE PROBLEM:
 * - In JMC, static initializers are transformed: <clinit> -> $staticInit
 * - The $staticInit method contains yield points (from instrumented field writes)
 * - Multiple threads can trigger lazy class loading simultaneously
 * - JVM's <clinit> is synchronized, but $staticInit is NOT
 * - When Thread A enters $staticInit and yields, Thread B can also enter $staticInit
 * - This creates a race condition on static field initialization
 *
 * ICEBERG SCENARIO:
 * - SnapshotSummary class has: static final MapJoiner MAP_JOINER = Joiner.on(",").withKeyValueSeparator("=");
 * - Instrumented code:
 *   public static void $staticInit() {
 *       Joiner.MapJoiner var10000 = Joiner.on(",").withKeyValueSeparator("=");
 *       JmcRuntimeUtils.writeEventWithoutYield(...);  // Instrumented write
 *       MAP_JOINER = var10000;
 *       JmcRuntime.yield();  // YIELD POINT!
 *   }
 * - Two threads both access SnapshotSummary.MAP_JOINER
 * - Both enter $staticInit simultaneously
 * - Thread A yields, Thread B continues
 * - Race condition on MAP_JOINER initialization
 *
 * This test reproduces the issue with simpler classes.
 */
public class ConcurrentStaticInitRaceTest {

    /**
     * Test 1: Two threads accessing a class with static initialization simultaneously.
     * This is the minimal reproduction of the Iceberg hang.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testTwoThreadsAccessStaticField() throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<String>> futures = new ArrayList<>();

        // Thread 1: Access the static field
        futures.add(executor.submit(() -> {
            String value = StaticPatterns.ClassWithStaticInit.VALUE;
            return value;
        }));

        // Thread 2: Access the same static field
        futures.add(executor.submit(() -> {
            String value = StaticPatterns.ClassWithStaticInit.VALUE;
            return value;
        }));

        // Wait for both threads
        String result1 = futures.get(0).get();

        String result2 = futures.get(1).get();

        executor.shutdown();

        // Both should get the same value
        assertEquals("initialized", result1);
        assertEquals("initialized", result2);
        //assert("initialized", result2);

    }

    /**
     * Test 2: Multiple threads accessing a class with complex static initialization.
     * This mimics the Iceberg SnapshotSummary scenario more closely.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testMultipleThreadsComplexStaticInit() throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<Future<Integer>> futures = new ArrayList<>();

        // Three threads all accessing the same class
        for (int i = 1; i <= 3; i++) {
            final int threadNum = i;
            futures.add(executor.submit(() -> {
                int value = StaticPatterns.ComplexStaticInit.COMPUTED_VALUE;
                return value;
            }));
        }

        // Wait for all threads
        List<Integer> results = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            Integer result = futures.get(i).get();
            results.add(result);
        }

        executor.shutdown();

        // All threads should get the same value
        assertEquals(42 , results.get(0));
        assertEquals(42 , results.get(1));
        assertEquals(42 , results.get(2));

    }

    /**
     * Test 3: Threads accessing multiple static fields from the same class.
     * This tests if the race affects multiple fields.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testMultipleStaticFields() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<String>> futures = new ArrayList<>();

        // Thread 1: Access field1
        futures.add(executor.submit(() -> {
            String value = StaticPatterns.MultipleStaticFields.FIELD1;
            return value;
        }));

        // Thread 2: Access field2 (triggers same static init)
        futures.add(executor.submit(() -> {
            String value = StaticPatterns.MultipleStaticFields.FIELD2;
            return value;
        }));

        // Wait for both threads
        String result1 = futures.get(0).get();
        String result2 = futures.get(1).get();

        executor.shutdown();

        assertEquals("field1", result1);
        assertEquals("field2", result2);

    }

    /**
     * Test 4: Static initialization with side effects (counter).
     * This tests if static init runs multiple times due to race.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    @JmcExpectAssertionFailure
    public void testStaticInitSideEffects() throws Exception {
        // Reset the counter before test
        StaticPatterns.StaticInitCounter.resetGlobalCounter();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Integer>> futures = new ArrayList<>();

        // Thread 1: Access the class
        futures.add(executor.submit(() -> {
            int value = StaticPatterns.StaticInitCounter.VALUE;
            return value;
        }));

        // Thread 2: Access the class
        futures.add(executor.submit(() -> {
            int value = StaticPatterns.StaticInitCounter.VALUE;
            return value;
        }));

        // Wait for both threads
        int result1 = futures.get(0).get();
        int result2 = futures.get(1).get();

        executor.shutdown();

        // Check how many times static init ran
        int globalCount = StaticPatterns.StaticInitCounter.getGlobalCounter();

        // In correct implementation, static init should run exactly once
        // If there's a race, it might run multiple times
        assertEquals(1 , globalCount);

    }

    /**
     * Test 5: Simulating the exact Iceberg scenario with method chaining.
     * This mimics: Joiner.on(",").withKeyValueSeparator("=")
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testMethodChainingStaticInit() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<String>> futures = new ArrayList<>();

        // Thread 1: Access the chained result
        futures.add(executor.submit(() -> {
            String value = StaticPatterns.IcebergLikeClass.JOINER;
            return value;
        }));

        // Thread 2: Access the chained result
        futures.add(executor.submit(() -> {
            String value = StaticPatterns.IcebergLikeClass.JOINER;
            return value;
        }));

        // Wait for both threads
        String result1 = futures.get(0).get();
        String result2 = futures.get(1).get();

        executor.shutdown();

        assertEquals("joiner-configured", result1);
        assertEquals("joiner-configured", result2);

    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testMultipleThreadsNestedStaticInit() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<Future<Integer>> futures = new ArrayList<>();

        // Three threads all accessing the same class
        for (int i = 1; i <= 3; i++) {
            final int threadNum = i;
            futures.add(executor.submit(() -> {
                int value = StaticPatterns.ComplexStaticInit.COMPUTED_VALUE;
                int value2 = StaticPatterns.B.y;
                return value;
            }));
        }

        // Wait for all threads
        List<Integer> results = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            Integer result = futures.get(i).get();
            results.add(result);
        }

        executor.shutdown();

        // All threads should get the same value
        assertEquals(42 , results.get(0));
        assertEquals(42 , results.get(1));
        assertEquals(42 , results.get(2));

    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false, strategy = "pct", timeout = 10000L)
    public void testTwoThreadsAccessStaticFieldPct() throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<String>> futures = new ArrayList<>();

        // Thread 1: Access the static field
        futures.add(executor.submit(() -> {
            String value = StaticPatterns.ClassWithStaticInit.VALUE;
            return value;
        }));

        // Thread 2: Access the same static field
        futures.add(executor.submit(() -> {
            String value = StaticPatterns.ClassWithStaticInit.VALUE;
            return value;
        }));

        // Wait for both threads
        String result1 = futures.get(0).get();

        String result2 = futures.get(1).get();

        executor.shutdown();

        // Both should get the same value
        assertEquals("initialized", result1);
        assertEquals("initialized", result2);

    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false, strategy = "pct", timeout = 10000L)
    public void testMultipleThreadsComplexStaticInitPct() throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<Future<Integer>> futures = new ArrayList<>();

        // Three threads all accessing the same class
        for (int i = 1; i <= 3; i++) {
            final int threadNum = i;
            futures.add(executor.submit(() -> {
                int value = StaticPatterns.ComplexStaticInit.COMPUTED_VALUE;
                return value;
            }));
        }

        // Wait for all threads
        List<Integer> results = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            Integer result = futures.get(i).get();
            results.add(result);
        }

        executor.shutdown();

        // All threads should get the same value
        assertEquals(42 , results.get(0));
        assertEquals(42 , results.get(1));
        assertEquals(42 , results.get(2));

    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false, strategy = "pct", timeout = 10000L)
    public void testMultipleStaticFieldsPct() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<String>> futures = new ArrayList<>();

        // Thread 1: Access field1
        futures.add(executor.submit(() -> {
            String value = StaticPatterns.MultipleStaticFields.FIELD1;
            return value;
        }));

        // Thread 2: Access field2 (triggers same static init)
        futures.add(executor.submit(() -> {
            String value = StaticPatterns.MultipleStaticFields.FIELD2;
            return value;
        }));

        // Wait for both threads
        String result1 = futures.get(0).get();
        String result2 = futures.get(1).get();

        executor.shutdown();

        assertEquals("field1", result1);
        assertEquals("field2", result2);

    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false, strategy = "pct", timeout = 10000L)
    @JmcExpectAssertionFailure
    public void testStaticInitSideEffectsPct() throws Exception {
        // Reset the counter before test
        StaticPatterns.StaticInitCounter.resetGlobalCounter();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Integer>> futures = new ArrayList<>();

        // Thread 1: Access the class
        futures.add(executor.submit(() -> {
            int value = StaticPatterns.StaticInitCounter.VALUE;
            return value;
        }));

        // Thread 2: Access the class
        futures.add(executor.submit(() -> {
            int value = StaticPatterns.StaticInitCounter.VALUE;
            return value;
        }));

        // Wait for both threads
        int result1 = futures.get(0).get();
        int result2 = futures.get(1).get();

        executor.shutdown();

        // Check how many times static init ran
        int globalCount = StaticPatterns.StaticInitCounter.getGlobalCounter();

        // In correct implementation, static init should run exactly once
        // If there's a race, it might run multiple times
        assertEquals(1 , globalCount);

    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false, strategy = "pct", timeout = 10000L)
    public void testMethodChainingStaticInitPct() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<String>> futures = new ArrayList<>();

        // Thread 1: Access the chained result
        futures.add(executor.submit(() -> {
            String value = StaticPatterns.IcebergLikeClass.JOINER;
            return value;
        }));

        // Thread 2: Access the chained result
        futures.add(executor.submit(() -> {
            String value = StaticPatterns.IcebergLikeClass.JOINER;
            return value;
        }));

        // Wait for both threads
        String result1 = futures.get(0).get();
        String result2 = futures.get(1).get();

        executor.shutdown();

        assertEquals("joiner-configured", result1);
        assertEquals("joiner-configured", result2);

    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false, strategy = "pct", timeout = 10000L)
    public void testMultipleThreadsNestedStaticInitPct() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<Future<Integer>> futures = new ArrayList<>();

        // Three threads all accessing the same class
        for (int i = 1; i <= 3; i++) {
            final int threadNum = i;
            futures.add(executor.submit(() -> {
                int value = StaticPatterns.ComplexStaticInit.COMPUTED_VALUE;
                int value2 = StaticPatterns.B.y;
                return value;
            }));
        }

        // Wait for all threads
        List<Integer> results = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            Integer result = futures.get(i).get();
            results.add(result);
        }

        executor.shutdown();

        // All threads should get the same value
        assertEquals(42 , results.get(0));
        assertEquals(42 , results.get(1));
        assertEquals(42 , results.get(2));

    }



}
