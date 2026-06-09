package org.mpi_sws.jmc.test.stress;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to demonstrate potential starvation when multiple tasks
 * perform many field accesses concurrently.
 *
 * Hypothesis: If JMC yields on every field access and uses random scheduling,
 * tasks that need many field accesses may starve and never complete.
 */
public class FieldAccessStarvationTest {

    /**
     * A simple class with many fields that will be accessed.
     */
    static class DataObject {
        int field1 = 1;
        int field2 = 2;
        int field3 = 3;
        int field4 = 4;
        int field5 = 5;
        int field6 = 6;
        int field7 = 7;
        int field8 = 8;
        int field9 = 9;
        int field10 = 10;
    }

    /**
     * Test with 2 tasks, each accessing many fields from their own objects.
     * If this hangs, it suggests starvation due to excessive yielding.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testTwoTasksManyFieldAccesses() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Integer>> futures = new ArrayList<>();

        // Task 1: Access fields from object1 many times
        DataObject object1 = new DataObject();
        futures.add(executor.submit(() -> {
            int sum = 0;
            // Access each field 10 times = 100 field reads total
            for (int i = 0; i < 10; i++) {
                sum += object1.field1;
                sum += object1.field2;
                sum += object1.field3;
                sum += object1.field4;
                sum += object1.field5;
                sum += object1.field6;
                sum += object1.field7;
                sum += object1.field8;
                sum += object1.field9;
                sum += object1.field10;
            }
            return sum;
        }));

        // Task 2: Access fields from object2 many times
        DataObject object2 = new DataObject();
        futures.add(executor.submit(() -> {
            int sum = 0;
            // Access each field 10 times = 100 field reads total
            for (int i = 0; i < 10; i++) {
                sum += object2.field1;
                sum += object2.field2;
                sum += object2.field3;
                sum += object2.field4;
                sum += object2.field5;
                sum += object2.field6;
                sum += object2.field7;
                sum += object2.field8;
                sum += object2.field9;
                sum += object2.field10;
            }
            return sum;
        }));

        // Main thread waits for both tasks
        int result1 = futures.get(0).get();

        int result2 = futures.get(1).get();
        assertNotEquals(0, result1);
        assertNotEquals(0, result2);
        executor.shutdown();
    }

    /**
     * Control test: Single task with many field accesses.
     * This should complete successfully.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testSingleTaskManyFieldAccesses() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(1);

        DataObject object = new DataObject();
        Future<Integer> future = executor.submit(() -> {
            int sum = 0;
            for (int i = 0; i < 10; i++) {
                sum += object.field1;
                sum += object.field2;
                sum += object.field3;
                sum += object.field4;
                sum += object.field5;
                sum += object.field6;
                sum += object.field7;
                sum += object.field8;
                sum += object.field9;
                sum += object.field10;
            }
            return sum;
        });

        int result = future.get();
        assertNotEquals(0, result);
        executor.shutdown();
    }

    /**
     * Test with fewer field accesses to see if there's a threshold.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testTwoTasksFewerFieldAccesses() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Integer>> futures = new ArrayList<>();

        DataObject object1 = new DataObject();
        futures.add(executor.submit(() -> {
            int sum = 0;
            // Only 2 iterations = 20 field reads
            for (int i = 0; i < 2; i++) {
                sum += object1.field1;
                sum += object1.field2;
                sum += object1.field3;
                sum += object1.field4;
                sum += object1.field5;
                sum += object1.field6;
                sum += object1.field7;
                sum += object1.field8;
                sum += object1.field9;
                sum += object1.field10;
            }
            return sum;
        }));

        DataObject object2 = new DataObject();
        futures.add(executor.submit(() -> {
            int sum = 0;
            for (int i = 0; i < 2; i++) {
                sum += object2.field1;
                sum += object2.field2;
                sum += object2.field3;
                sum += object2.field4;
                sum += object2.field5;
                sum += object2.field6;
                sum += object2.field7;
                sum += object2.field8;
                sum += object2.field9;
                sum += object2.field10;
            }
            return sum;
        }));

        int result1 = futures.get(0).get();
        int result2 = futures.get(1).get();
        assertNotEquals(0, result1);
        assertNotEquals(0, result2);
        executor.shutdown();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false, strategy = "pct", timeout = 10000L)
    public void testTwoTasksManyFieldAccessesPct() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Integer>> futures = new ArrayList<>();

        // Task 1: Access fields from object1 many times
        DataObject object1 = new DataObject();
        futures.add(executor.submit(() -> {
            int sum = 0;
            // Access each field 10 times = 100 field reads total
            for (int i = 0; i < 10; i++) {
                sum += object1.field1;
                sum += object1.field2;
                sum += object1.field3;
                sum += object1.field4;
                sum += object1.field5;
                sum += object1.field6;
                sum += object1.field7;
                sum += object1.field8;
                sum += object1.field9;
                sum += object1.field10;
            }
            return sum;
        }));

        // Task 2: Access fields from object2 many times
        DataObject object2 = new DataObject();
        futures.add(executor.submit(() -> {
            int sum = 0;
            // Access each field 10 times = 100 field reads total
            for (int i = 0; i < 10; i++) {
                sum += object2.field1;
                sum += object2.field2;
                sum += object2.field3;
                sum += object2.field4;
                sum += object2.field5;
                sum += object2.field6;
                sum += object2.field7;
                sum += object2.field8;
                sum += object2.field9;
                sum += object2.field10;
            }
            return sum;
        }));

        // Main thread waits for both tasks
        int result1 = futures.get(0).get();

        int result2 = futures.get(1).get();
        assertNotEquals(0, result1);
        assertNotEquals(0, result2);
        executor.shutdown();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false, strategy = "pct", timeout = 10000L)
    public void testSingleTaskManyFieldAccessesPct() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(1);

        DataObject object = new DataObject();
        Future<Integer> future = executor.submit(() -> {
            int sum = 0;
            for (int i = 0; i < 10; i++) {
                sum += object.field1;
                sum += object.field2;
                sum += object.field3;
                sum += object.field4;
                sum += object.field5;
                sum += object.field6;
                sum += object.field7;
                sum += object.field8;
                sum += object.field9;
                sum += object.field10;
            }
            return sum;
        });

        int result = future.get();
        assertNotEquals(0, result);
        executor.shutdown();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false, strategy = "pct", timeout = 10000L)
    public void testTwoTasksFewerFieldAccessesPct() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Integer>> futures = new ArrayList<>();

        DataObject object1 = new DataObject();
        futures.add(executor.submit(() -> {
            int sum = 0;
            // Only 2 iterations = 20 field reads
            for (int i = 0; i < 2; i++) {
                sum += object1.field1;
                sum += object1.field2;
                sum += object1.field3;
                sum += object1.field4;
                sum += object1.field5;
                sum += object1.field6;
                sum += object1.field7;
                sum += object1.field8;
                sum += object1.field9;
                sum += object1.field10;
            }
            return sum;
        }));

        DataObject object2 = new DataObject();
        futures.add(executor.submit(() -> {
            int sum = 0;
            for (int i = 0; i < 2; i++) {
                sum += object2.field1;
                sum += object2.field2;
                sum += object2.field3;
                sum += object2.field4;
                sum += object2.field5;
                sum += object2.field6;
                sum += object2.field7;
                sum += object2.field8;
                sum += object2.field9;
                sum += object2.field10;
            }
            return sum;
        }));

        int result1 = futures.get(0).get();
        int result2 = futures.get(1).get();
        assertNotEquals(0, result1);
        assertNotEquals(0, result2);
        executor.shutdown();
    }
}
