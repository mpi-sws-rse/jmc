package org.mpi_sws.jmc.test.stress;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Test to reproduce the Finalizer thread issue seen in HadoopStreams.
 * When objects with finalizers are garbage collected while other tasks are running,
 * the Finalizer thread accesses instrumented fields causing scheduler conflicts.
 */
public class FinalizerThreadTest {

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
    public void testFinalizerConflictWithConcurrentTasks() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Create objects with finalizers
        for (int i = 0; i < 5; i++) {
            StreamLikeObject obj = new StreamLikeObject();
            obj.closed = false;
            // Don't close it - let finalizer handle it
        }

        // Submit concurrent tasks while finalizers might run
        Future<Integer> f1 = executor.submit(() -> {
            // Trigger GC while task is running
            System.gc();
            Thread.sleep(50);
            return 1;
        });

        Future<Integer> f2 = executor.submit(() -> {
            Thread.sleep(50);
            return 2;
        });

        // Wait for tasks
        f1.get();
        f2.get();

        executor.shutdown();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, strategy = "pct", timeout = 10000L)
    public void testFinalizerConflictWithConcurrentTasksPct() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Create objects with finalizers
        for (int i = 0; i < 5; i++) {
            StreamLikeObject obj = new StreamLikeObject();
            obj.closed = false;
            // Don't close it - let finalizer handle it
        }

        // Submit concurrent tasks while finalizers might run
        Future<Integer> f1 = executor.submit(() -> {
            // Trigger GC while task is running
            System.gc();
            Thread.sleep(50);
            return 1;
        });

        Future<Integer> f2 = executor.submit(() -> {
            Thread.sleep(50);
            return 2;
        });

        // Wait for tasks
        f1.get();
        f2.get();

        executor.shutdown();
    }

    /**
     * Mimics HadoopSeekableInputStream/HadoopPositionOutputStream
     * with a finalizer that checks a 'closed' field.
     */
    static class StreamLikeObject {
        boolean closed;

        @SuppressWarnings({"checkstyle:NoFinalizer", "Finalize"})
        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            // This mimics HadoopStreams finalizer behavior
            // Reading 'closed' field triggers JMC instrumentation
            if (!closed) {
                System.out.println("Finalizer: Stream was not closed properly");
            }
        }
    }
}
