package org.mpi_sws.jmc.test.stress;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Stress test to reproduce the ThreadPool + ReadWriteVisitor interaction issue.
 *
 * This test simulates the Iceberg scenario where:
 * 1. Multiple threads from a ThreadPool execute tasks
 * 2. Tasks access shared fields (triggering ReadWriteVisitor instrumentation)
 * 3. ReadWriteVisitor inserts yields after every field access
 * 4. Multiple threads try to yield the same task ID simultaneously
 *
 * Expected behavior WITHOUT ReadWriteVisitor: Test completes successfully
 * Expected behavior WITH ReadWriteVisitor: Test may hang or fail with "Yielding an already paused task"
 */
public class ThreadPoolReadWriteInteractionTest {

    /**
     * Simulates a simple data structure similar to Iceberg's catalog operations.
     * Has fields that will be instrumented by ReadWriteVisitor.
     */
    static class SharedDataStore {
        private int counter = 0;
        private String lastOperation = "";
        private final ReentrantLock lock = new ReentrantLock();

        public void performOperation(String operation) {
            lock.lock();
            try {
                // These field accesses will be instrumented by ReadWriteVisitor
                // Each access will trigger: readEvent + yield
                int currentCounter = counter;  // READ - triggers yield
                lastOperation = operation;      // WRITE - triggers yield
                counter = currentCounter + 1;   // WRITE - triggers yield
            } finally {
                lock.unlock();
            }
        }

        public int getCounter() {
            lock.lock();
            try {
                return counter;  // READ - triggers yield
            } finally {
                lock.unlock();
            }
        }

        public String getLastOperation() {
            lock.lock();
            try {
                return lastOperation;  // READ - triggers yield
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Minimal test: 2 threads, simple field accesses.
     * This should reproduce the "Yielding an already paused task" error.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testMinimalThreadPoolWithFieldAccess() throws Exception {
        SharedDataStore store = new SharedDataStore();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Integer>> futures = new ArrayList<>();

        // Submit 2 tasks that access shared fields
        for (int i = 0; i < 2; i++) {
            final int taskId = i;
            Future<Integer> future = executor.submit(() -> {
                store.performOperation("task-" + taskId);
                return taskId;
            });
            futures.add(future);
        }

        // Wait for completion
        for (Future<Integer> f : futures) {
            f.get();
        }

        executor.shutdown();

        // Verify results
        int finalCounter = store.getCounter();
        assertEquals(2, finalCounter);
    }

    /**
     * Test with more field accesses to increase likelihood of concurrent yields.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testThreadPoolWithMultipleFieldAccesses() throws Exception {
        SharedDataStore store = new SharedDataStore();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Void>> futures = new ArrayList<>();

        // Each task performs multiple operations
        for (int i = 0; i < 2; i++) {
            final int taskId = i;
            Future<Void> future = executor.submit(() -> {
                for (int j = 0; j < 3; j++) {
                    store.performOperation("task-" + taskId + "-op-" + j);
                    // Read operations to trigger more yields
                    int counter = store.getCounter();
                    String lastOp = store.getLastOperation();
                }
                return null;
            });
            futures.add(future);
        }

        // Wait for completion
        for (Future<Void> f : futures) {
            f.get();
        }

        executor.shutdown();

    }

    /**
     * Test with nested field accesses (similar to Iceberg's commit logic).
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testThreadPoolWithNestedFieldAccesses() throws Exception {
        NestedDataStructure data = new NestedDataStructure();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            final int taskId = i;
            Future<Void> future = executor.submit(() -> {
                data.performNestedOperation(taskId);
                return null;
            });
            futures.add(future);
        }

        for (Future<Void> f : futures) {
            f.get();
        }

        executor.shutdown();

    }

    /**
     * Nested data structure with multiple levels of field accesses.
     */
    static class NestedDataStructure {
        private InnerData inner = new InnerData();
        private int outerCounter = 0;

        static class InnerData {
            private int value = 0;
            private String status = "initial";

            public void update(int newValue) {
                this.value = newValue;      // WRITE - triggers yield
                this.status = "updated";    // WRITE - triggers yield
            }

            public int getValue() {
                return value;  // READ - triggers yield
            }
        }

        public void performNestedOperation(int taskId) {
            // Access outer field
            int current = outerCounter;  // READ - triggers yield

            // Access inner fields
            inner.update(taskId);  // Multiple yields inside
            int innerValue = inner.getValue();  // READ - triggers yield

            // Update outer field
            outerCounter = current + innerValue;  // WRITE - triggers yield

        }
    }

    /**
     * Test that simulates the exact Iceberg pattern:
     * - ThreadPool with 2 threads
     * - Tasks that perform "commit-like" operations with locks
     * - Multiple field accesses under lock
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testIcebergLikeCommitPattern() throws Exception {
        CatalogSimulator catalog = new CatalogSimulator();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Boolean>> futures = new ArrayList<>();

        // Simulate concurrent commits
        for (int i = 0; i < 3; i++) {
            final int fileId = i;
            Future<Boolean> future = executor.submit(() -> {
                boolean success = catalog.commit(fileId);
                return success;
            });
            futures.add(future);
        }

        // Wait for all commits
        int successCount = 0;
        for (Future<Boolean> f : futures) {
            if (f.get()) {
                successCount++;
            }
        }

        executor.shutdown();

        assertEquals(3, successCount);
    }

    /**
     * Simulates a simplified catalog with commit operations.
     */
    static class CatalogSimulator {
        private int snapshotCount = 0;
        private String lastCommitFile = "";
        private final ReentrantLock commitLock = new ReentrantLock();

        public boolean commit(int fileId) {
            commitLock.lock();
            try {
                // Read current state (triggers yields)
                int currentSnapshots = snapshotCount;
                String lastFile = lastCommitFile;

                // Simulate some processing with field accesses
                for (int i = 0; i < 3; i++) {
                    // Each iteration reads and writes fields
                    int temp = snapshotCount;  // READ
                    snapshotCount = temp;      // WRITE
                }

                // Update state (triggers yields)
                lastCommitFile = "file-" + fileId;
                snapshotCount = currentSnapshots + 1;

                return true;
            } finally {
                commitLock.unlock();
            }
        }

        public int getSnapshotCount() {
            commitLock.lock();
            try {
                return snapshotCount;  // READ - triggers yield
            } finally {
                commitLock.unlock();
            }
        }
    }

    /**
     * Stress test with many threads and operations.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testStressThreadPoolWithManyOperations() throws Exception {
        SharedDataStore store = new SharedDataStore();

        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<Future<Void>> futures = new ArrayList<>();

        // Submit many tasks
        for (int i = 0; i < 5; i++) {
            final int taskId = i;
            Future<Void> future = executor.submit(() -> {
                for (int j = 0; j < 2; j++) {
                    store.performOperation("task-" + taskId + "-" + j);
                }
                return null;
            });
            futures.add(future);
        }

        for (Future<Void> f : futures) {
            f.get();
        }

        executor.shutdown();

        int finalCounter = store.getCounter();
        assertEquals(10, finalCounter);
    }
}
