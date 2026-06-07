package org.mpi_sws.jmc.test.caffeine;

import org.junit.jupiter.api.Disabled;
import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reproduces the JDK monitor / yield deadlock issue.
 *
 * Caffeine calls user code (removal listeners, weighers) from inside
 * ConcurrentHashMap.compute(). The JDK holds an internal bin-level monitor
 * during compute(). If JMC yields inside the compute lambda (due to field
 * read/write instrumentation), another JMC-scheduled task that also tries
 * to access the same ConcurrentHashMap bin will block on the real JVM monitor.
 * But JMC's scheduler doesn't know about this JVM-level blocking, so it may
 * never schedule the lock holder back, causing a deadlock.
 *
 * This test creates exactly that scenario:
 * - Thread A calls map.compute(key, ...) which holds an internal JDK monitor on the bin
 * - Inside the compute lambda, instrumented field reads yield to the scheduler
 * - Thread B is scheduled and tries map.get(key) or map.compute(key, ...)
 * - Thread B blocks on the JDK monitor, but JMC doesn't know it's blocked
 *
 * EXPECTED: Deadlock/hang with current JMC (yields inside compute are unsafe)
 * FIX NEEDED: Suppress yields while inside ConcurrentHashMap.compute/merge/computeIfAbsent
 */
public class ConcurrentHashMapYieldTest {

    /**
     * Two threads both call compute() on the same key.
     * Inside the compute function, we access an instrumented field (which triggers a yield).
     * With current JMC, this can deadlock because the JDK bin-level lock is held during compute.
     */
    @Disabled
    public void testComputeOnSameKeyDeadlock() throws Exception {
        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
        map.put("key", 0);

        // Shared mutable state accessed inside compute — triggers instrumented field access
        AtomicInteger sharedCounter = new AtomicInteger(0);

        Thread t1 = new Thread(() -> {
            map.compute("key", (k, v) -> {
                // This field read is instrumented and yields.
                // The JDK bin-level monitor is held here.
                int current = sharedCounter.get();
                return (v == null ? 0 : v) + 1;
            });
        });

        Thread t2 = new Thread(() -> {
            map.compute("key", (k, v) -> {
                int current = sharedCounter.get();
                return (v == null ? 0 : v) + 1;
            });
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // Both computes should have completed; final value should be 2
        assertEquals(2, map.get("key").intValue(),
                "Both compute operations should have completed");
    }

    /**
     * One thread calls computeIfAbsent, another calls get on the same key.
     * computeIfAbsent holds the JDK bin lock while the mapping function runs.
     * If JMC yields inside the mapping function and schedules thread B,
     * thread B's get() on the same bin will block on the JDK monitor.
     */
    @Disabled
    public void testComputeIfAbsentVsGet() throws Exception {
        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
        AtomicInteger result = new AtomicInteger(-1);

        // Shared field to force instrumented access inside the lambda
        AtomicInteger expensiveComputation = new AtomicInteger(42);

        Thread t1 = new Thread(() -> {
            map.computeIfAbsent("key", k -> {
                // Instrumented field access here triggers yield
                return expensiveComputation.get();
            });
        });

        Thread t2 = new Thread(() -> {
            // This get() on the same key may block on the JDK bin monitor
            // if the scheduler yields T1 inside computeIfAbsent and schedules T2
            Integer val = map.get("key");
            if (val != null) {
                result.set(val);
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // The key must exist after both threads complete
        assertNotNull(map.get("key"), "Key must be present after computeIfAbsent");
        assertEquals(42, map.get("key").intValue());
    }

    /**
     * Simpler variant: compute() with a plain field access inside (not atomic).
     * This is more realistic for Caffeine which accesses weigher results,
     * node fields, etc. inside compute lambdas.
     */
    @Disabled
    public void testComputeWithPlainFieldAccess() throws Exception {
        ConcurrentHashMap<Integer, Integer> map = new ConcurrentHashMap<>();
        for (int i = 0; i < 5; i++) {
            map.put(i, 0);
        }

        // Plain field (not atomic) — GETFIELD instrumentation will yield
        int[] weights = new int[]{1, 2, 3, 4, 5};

        Thread t1 = new Thread(() -> {
            map.compute(0, (k, v) -> {
                // Reading weights[0] triggers GETFIELD on the array ref + AALOAD
                return v + weights[0];
            });
        });

        Thread t2 = new Thread(() -> {
            map.compute(0, (k, v) -> {
                return v + weights[1];
            });
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // Both computes ran on key 0, result should be weights[0] + weights[1] = 3
        int finalVal = map.get(0);
        assertEquals(3, finalVal,
                "Both compute ops should apply: 0 + 1 + 2 = 3");
    }
}
