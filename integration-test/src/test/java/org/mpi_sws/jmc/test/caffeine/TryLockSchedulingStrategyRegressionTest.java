package org.mpi_sws.jmc.test.caffeine;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;

import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for nonblocking lock acquire events under alternate scheduling strategies.
 *
 * <p>{@link ReentrantLock#tryLock()} is rewritten to JMC's lock shim. The shim asks the scheduler
 * for a boolean result through an unfair nonblocking lock-acquire event. Random scheduling already
 * attaches that result to the next scheduling choice. PCT and fair-PCT must do the same.
 */
public class TryLockSchedulingStrategyRegressionTest {

    @JmcCheck
    @JmcCheckConfiguration(
            numIterations = 100,
            strategy = "random",
            timeout = 10000L,
            debug = false)
    public void randomDeliversTryLockResult() {
        exerciseUncontendedTryLock();
    }

    @JmcCheck
    @JmcCheckConfiguration(
            numIterations = 100,
            strategy = "pct",
            bugDepth = 1,
            timeout = 10000L,
            debug = false)
    public void pctDeliversTryLockResult() {
        exerciseUncontendedTryLock();
    }

    @JmcCheck
    @JmcCheckConfiguration(
            numIterations = 100,
            strategy = "fair-pct",
            bugDepth = 1,
            pctFairBound = 1,
            timeout = 10000L,
            debug = false)
    public void fairPctDeliversTryLockResult() {
        exerciseUncontendedTryLock();
    }

    private static void exerciseUncontendedTryLock() {
        ReentrantLock lock = new ReentrantLock();
        boolean acquired = lock.tryLock();
        try {
            assertTrue(acquired, "An uncontended tryLock must return true");
        } finally {
            if (acquired) {
                lock.unlock();
            }
        }
    }
}
