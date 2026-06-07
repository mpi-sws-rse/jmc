package org.mpi_sws.jmc.test.caffeine;

import org.junit.jupiter.api.Disabled;
import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reproduces the "No current task" error with CompletableFuture callbacks.
 *
 * Caffeine uses CompletableFuture for async loading/refresh. When a future
 * completes, its callbacks (thenApply, whenComplete, etc.) run on a thread
 * that may not have a registered JMC task context. This causes:
 *   JmcRuntime.currentTask() -> "No current task" -> HaltExecutionException
 *
 * The question is: is this because callbacks run on a thread pool thread that
 * was never registered with JMC, or because the task context was already
 * terminated before the callback ran?
 *
 * This test explores both hypotheses:
 * 1. Callback on the completing thread (thenApply with synchronous executor)
 * 2. Callback on a separate executor thread
 * 3. Callback chaining where intermediate completions happen asynchronously
 *
 * EXPECTED: Some of these should trigger "No current task" with current JMC.
 * This will help us understand whether the issue is:
 *   (a) Callbacks running on ForkJoinPool.commonPool() threads not registered with JMC
 *   (b) Task context being cleared too early (after the submitting task yields)
 *   (c) Something else entirely
 */
public class CompletableFutureCallbackTest {

    /**
     * Basic case: complete a future from one thread, observe the callback runs.
     * Uses Runnable::run executor so callback runs on the completing thread.
     * This should work because the completing thread has a JMC task context.
     */
    @Disabled
    public void testSynchronousCallback() throws Exception {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        AtomicInteger callbackResult = new AtomicInteger(-1);

        // Register callback — will run on completing thread since no async executor
        future.thenAccept(val -> {
            callbackResult.set(val * 2);
        });

        Thread t1 = new Thread(() -> {
            future.complete(21);
        });

        t1.start();
        t1.join();

        assertEquals(42, callbackResult.get(),
                "Callback should have run with result 42");
    }

    /**
     * Callback runs on a JMC-managed executor.
     * This should work because the executor creates JMC tasks.
     */
    
    @Disabled
    public void testCallbackOnManagedExecutor() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(1);
        CompletableFuture<Integer> future = new CompletableFuture<>();
        AtomicInteger callbackResult = new AtomicInteger(-1);

        // Register async callback on our managed executor
        future.thenAcceptAsync(val -> {
            callbackResult.set(val * 2);
        }, executor);

        Thread t1 = new Thread(() -> {
            future.complete(21);
        });

        t1.start();
        t1.join();

        // Wait for async callback
        Thread.sleep(10);
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);

        assertEquals(42, callbackResult.get(),
                "Async callback on managed executor should have run");
    }

    /**
     * Callback runs on ForkJoinPool.commonPool() (the default for thenAcceptAsync
     * without explicit executor). This is the problematic case — commonPool threads
     * are not registered with JMC.
     *
     * This is what happens in Caffeine when async loading completes.
     */
    @Disabled
    public void testCallbackOnCommonPool() throws Exception {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        AtomicInteger callbackResult = new AtomicInteger(-1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        // thenAcceptAsync without executor uses ForkJoinPool.commonPool()
        future.thenAcceptAsync(val -> {
            try {
                callbackResult.set(val * 2);
            } catch (Throwable t) {
                error.set(t);
            }
        });

        Thread t1 = new Thread(() -> {
            future.complete(21);
        });

        t1.start();
        t1.join();

        // Give commonPool time to run the callback
        Thread.sleep(50);

        if (error.get() != null) {
            fail("Callback threw: " + error.get().getMessage());
        }
        assertEquals(42, callbackResult.get(),
                "Async callback on commonPool should have run");
    }

    /**
     * Chained callbacks: future.thenApply().thenAccept()
     * Each stage may run on a different thread.
     */
    @Disabled
    public void testChainedCallbacks() throws Exception {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        AtomicInteger finalResult = new AtomicInteger(-1);

        future
            .thenApply(val -> val + 1)    // 21 -> 22
            .thenApply(val -> val * 2)    // 22 -> 44
            .thenAccept(val -> finalResult.set(val));

        Thread t1 = new Thread(() -> {
            future.complete(21);
        });

        t1.start();
        t1.join();

        assertEquals(44, finalResult.get(),
                "Chained callbacks should produce 44");
    }

    /**
     * whenComplete callback — this is what Caffeine uses for refresh completion.
     * The callback receives both the value and any exception.
     */
    @Disabled
    public void testWhenComplete() throws Exception {
        CompletableFuture<String> future = new CompletableFuture<>();
        AtomicReference<String> receivedValue = new AtomicReference<>();
        AtomicReference<Throwable> receivedException = new AtomicReference<>();

        future.whenComplete((val, ex) -> {
            receivedValue.set(val);
            receivedException.set(ex);
        });

        Thread t1 = new Thread(() -> {
            future.complete("hello");
        });

        t1.start();
        t1.join();

        assertEquals("hello", receivedValue.get());
        assertNull(receivedException.get());
    }

    /**
     * Two threads racing to complete the same future.
     * Only one should win; callbacks should see the winner's value.
     */
    @Disabled
    public void testRacingCompletions() throws Exception {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        AtomicInteger callbackValue = new AtomicInteger(-1);

        future.thenAccept(callbackValue::set);

        Thread t1 = new Thread(() -> future.complete(1));
        Thread t2 = new Thread(() -> future.complete(2));

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // One of them won — callback should have a valid value
        int val = callbackValue.get();
        assertTrue(val == 1 || val == 2,
                "Callback should see either 1 or 2, got: " + val);
    }
}
