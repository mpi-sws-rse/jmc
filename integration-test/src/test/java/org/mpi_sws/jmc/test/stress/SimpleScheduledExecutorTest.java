package org.mpi_sws.jmc.test.stress;

import org.junit.jupiter.api.Disabled;
import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.annotations.JmcExpectAssertionFailure;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test to verify basic ScheduledExecutorService functionality.
 */
public class SimpleScheduledExecutorTest {

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testSimpleSchedule() throws Exception {
        ScheduledExecutorService pool = Executors.newScheduledThreadPool(1);
        AtomicInteger counter = new AtomicInteger(0);

        try {
            ScheduledFuture<?> future = pool.schedule(() -> {
                counter.incrementAndGet();
            }, 0, TimeUnit.MILLISECONDS);

            // Wait for task to complete
            future.get();

            assertEquals(1, counter.get(), "Task should have run exactly once");
        } finally {
            pool.shutdown();
        }
    }

    // TODO: Fix the following test
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    @JmcExpectAssertionFailure
    @Disabled
    public void testScheduleWithFixedDelayRunsOnce() throws Exception {
        ScheduledExecutorService pool = Executors.newScheduledThreadPool(1);
        AtomicInteger counter = new AtomicInteger(0);

        try {
            ScheduledFuture<?> future = pool.scheduleWithFixedDelay(() -> {
                counter.incrementAndGet();
            }, 0, 1, TimeUnit.MILLISECONDS);

            // In JMC, periodic tasks run once, so we can wait for completion
            // But periodic tasks don't complete, so we need to cancel and check
            Thread.yield(); // Give the task a chance to run
            Thread.yield();
            Thread.yield();

            future.cancel(false);

            assertTrue(counter.get() >= 1, "Task should have run at least once, got: " + counter.get());
        } finally {
            pool.shutdown();
        }
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false)
    public void testMultipleScheduledTasks() throws Exception {
        ScheduledExecutorService pool = Executors.newScheduledThreadPool(2);
        AtomicInteger counter = new AtomicInteger(0);

        try {
            ScheduledFuture<?> future1 = pool.schedule(() -> counter.incrementAndGet(), 0, TimeUnit.MILLISECONDS);
            ScheduledFuture<?> future2 = pool.schedule(() -> counter.incrementAndGet(), 0, TimeUnit.MILLISECONDS);

            future1.get();
            future2.get();

            assertEquals(2, counter.get(), "Both tasks should have run");
        } finally {
            pool.shutdown();
        }
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false, strategy = "pct", timeout = 10000L)
    public void testSimpleSchedulePct() throws Exception {
        ScheduledExecutorService pool = Executors.newScheduledThreadPool(1);
        AtomicInteger counter = new AtomicInteger(0);

        try {
            ScheduledFuture<?> future = pool.schedule(() -> {
                counter.incrementAndGet();
            }, 0, TimeUnit.MILLISECONDS);

            // Wait for task to complete
            future.get();

            assertEquals(1, counter.get(), "Task should have run exactly once");
        } finally {
            pool.shutdown();
        }
    }

    // TODO: Fix the following test
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false, strategy = "pct", timeout = 10000L)
    @JmcExpectAssertionFailure
    @Disabled
    public void testScheduleWithFixedDelayRunsOncePct() throws Exception {
        ScheduledExecutorService pool = Executors.newScheduledThreadPool(1);
        AtomicInteger counter = new AtomicInteger(0);

        try {
            ScheduledFuture<?> future = pool.scheduleWithFixedDelay(() -> {
                counter.incrementAndGet();
            }, 0, 1, TimeUnit.MILLISECONDS);

            // In JMC, periodic tasks run once, so we can wait for completion
            // But periodic tasks don't complete, so we need to cancel and check
            Thread.yield(); // Give the task a chance to run
            Thread.yield();
            Thread.yield();

            future.cancel(false);

            assertTrue(counter.get() >= 1, "Task should have run at least once, got: " + counter.get());
        } finally {
            pool.shutdown();
        }
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = false, strategy = "pct", timeout = 10000L)
    public void testMultipleScheduledTasksPct() throws Exception {
        ScheduledExecutorService pool = Executors.newScheduledThreadPool(2);
        AtomicInteger counter = new AtomicInteger(0);

        try {
            ScheduledFuture<?> future1 = pool.schedule(() -> counter.incrementAndGet(), 0, TimeUnit.MILLISECONDS);
            ScheduledFuture<?> future2 = pool.schedule(() -> counter.incrementAndGet(), 0, TimeUnit.MILLISECONDS);

            future1.get();
            future2.get();

            assertEquals(2, counter.get(), "Both tasks should have run");
        } finally {
            pool.shutdown();
        }
    }
}
