package org.mpi_sws.jmc.test.caffeine;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reproduces the tryLock() gap in JMC.
 *
 * Caffeine's BoundedLocalCache uses tryLock() on its eviction lock:
 *   if (evictionLock.tryLock()) {
 *       try { maintenance(); } finally { evictionLock.unlock(); }
 *   }
 * If tryLock() fails (lock is held by another thread doing maintenance),
 * the current thread skips maintenance entirely.
 *
 * JMC currently lacks tryLock() support in JmcReentrantLock, so it either:
 * - Always succeeds (never exploring the "maintenance skipped" path)
 * - Throws NoSuchMethodError
 *
 * This test creates the same pattern: two threads race to acquire a lock via tryLock().
 * The property: a shared counter protected by tryLock-guarded critical section should
 * show non-deterministic behavior depending on who gets the lock first.
 *
 * EXPECTED: This test should demonstrate that tryLock() contention paths are explored.
 * CURRENT BEHAVIOR: Will likely fail/error because JmcReentrantLock has no tryLock().
 */
public class TryLockTest {

    /**
     * Two threads both try to acquire the eviction lock via tryLock().
     * Under contention, one should succeed and one should fail.
     * Property: maintenanceCount should be at least 1 (at least one thread gets the lock).
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100, debug = false)
    public void testTryLockContention() throws Exception {
        ReentrantLock evictionLock = new ReentrantLock();
        AtomicInteger maintenanceCount = new AtomicInteger(0);
        AtomicInteger skippedCount = new AtomicInteger(0);

        Thread t1 = new Thread(() -> {
            if (evictionLock.tryLock()) {
                try {
                    maintenanceCount.incrementAndGet();
                } finally {
                    evictionLock.unlock();
                }
            } else {
                skippedCount.incrementAndGet();
            }
        });

        Thread t2 = new Thread(() -> {
            if (evictionLock.tryLock()) {
                try {
                    maintenanceCount.incrementAndGet();
                } finally {
                    evictionLock.unlock();
                }
            } else {
                skippedCount.incrementAndGet();
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // At least one thread must have gotten the lock
        assertTrue(maintenanceCount.get() >= 1,
                "At least one thread should have performed maintenance");
        // Total should be 2 (both succeeded or one skipped)
        assertEquals(2, maintenanceCount.get() + skippedCount.get(),
                "Every thread must either do maintenance or skip");
    }

    /**
     * Tests tryLock() when the lock is definitely free — should always succeed.
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100, debug = false)
    public void testTryLockUncontended() throws Exception {
        ReentrantLock lock = new ReentrantLock();
        AtomicInteger acquired = new AtomicInteger(0);

        Thread t1 = new Thread(() -> {
            if (lock.tryLock()) {
                try {
                    acquired.incrementAndGet();
                } finally {
                    lock.unlock();
                }
            }
        });

        t1.start();
        t1.join();

        assertEquals(1, acquired.get(), "Uncontended tryLock should always succeed");
    }
}
