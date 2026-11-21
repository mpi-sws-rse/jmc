package org.mpi_sws.jmc.strategies.tracker;

import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JMC-SPEED-2 tester rounds 03-05: the concurrent workloads of
 * {@code J2TrackerTest.synchronizedCandidateSet_noDeadlock}, run in a disposable JVM so that the test
 * JVM never hosts a thread that could block. Every worker catches every Throwable into
 * {@code failures}; every wait is bounded; the JVM ends by {@link Runtime#halt}, which also ends the
 * deadlocked threads of part C, and a watchdog halts it after 150 s whatever happens.
 * <ul>
 *   <li>A: a synchronized candidate set held by a holder thread that calls the tracker while a caller
 *       runs the public path 20 000 times; the caller must finish within 20 s (no deadlock), the
 *       holder must stop when told (the stop flag is set in a finally).</li>
 *   <li>B: the strategy from three threads (events, readers, resets); every step must return
 *       normally, every worker must finish all its steps within 30 s.</li>
 *   <li>C (NOTE, finding F2): the package-private {@code updateAndRetainActive} holds the tracker lock
 *       while it calls the foreign set's {@code retainAll}. The holder takes the set's monitor, waits
 *       until the caller is BLOCKED, then calls the tracker; {@link ThreadMXBean#findDeadlockedThreads}
 *       (or both threads finishing) decides, polled for at most 10 s.</li>
 * </ul>
 * Output, one per line: {@code J2PROBE-COUNTS caller=<n> events=<n> readers=<n> resets=<n>}, a NOTE,
 * then {@code J2PROBE-OK deadlocked=<b>} with exit 0 only if no failure, otherwise
 * {@code J2PROBE-FAIL [...]} with exit 1 (2: main itself failed; 4: the watchdog fired).
 */
public final class J2DeadlockProbe {
    public static void main(String[] args) {
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(150_000);
            } catch (InterruptedException e) {
                // halted below anyway
            }
            System.out.println("J2PROBE-FAIL watchdog: 150 s");
            System.out.flush();
            Runtime.getRuntime().halt(4);
        }, "j2-probe-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        int rc;
        try {
            rc = run();
        } catch (Throwable t) {
            System.out.println("J2PROBE-FAIL main: " + t);
            rc = 2;
        }
        System.out.flush();
        Runtime.getRuntime().halt(rc);
    }

    static Thread daemon(String name, Runnable r) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }

    static int run() throws Exception {
        ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();

        // A: the public path with a synchronized set held across tracker calls
        TrackTasks t = new TrackTasks();
        for (long i = 1; i <= 50; i++) {
            t.updateEvent(J2TrackerTest.start(i));
        }
        Set<Long> sync = Collections.synchronizedSet(new HashSet<>(List.of(1L, 2L, 3L)));
        AtomicBoolean stop = new AtomicBoolean();
        int[] callerDone = new int[1];
        Thread holder = daemon("j2-public-holder", () -> {
            try {
                long k = 100;
                while (!stop.get()) {
                    synchronized (sync) {
                        t.updateEvent(J2TrackerTest.start(k++));
                        t.updateEvent(J2TrackerTest.finish(k - 1));
                    }
                }
            } catch (Throwable x) {
                failures.add("holder: " + x);
            }
        });
        Thread caller = daemon("j2-public-caller", () -> {
            try {
                for (int i = 0; i < 20000; i++) {
                    sync.retainAll(t.updateEvent(J2TrackerTest.join(1, 2)));
                    sync.add(1L);
                    callerDone[0]++;
                }
            } catch (Throwable x) {
                failures.add("caller: " + x);
            }
        });
        try {
            holder.start();
            caller.start();
            caller.join(20_000);
        } finally {
            stop.set(true);
        }
        holder.join(20_000);
        if (caller.isAlive()) {
            failures.add("public path: caller not finished in 20 s (deadlock)");
        }
        if (holder.isAlive()) {
            failures.add("public path: holder not stopped in 20 s");
        }

        // B: the strategy from three threads
        List<JmcRuntimeEvent> evs = J2TrackerTest.random(77, 20000);
        J2TrackerTest.NewStrategy s = new J2TrackerTest.NewStrategy(List.of(new TrackTasks()));
        int[] done = new int[3];
        Thread events = daemon("j2-strategy-events", () -> {
            for (JmcRuntimeEvent e : evs) {
                if (e != J2TrackerTest.RESET) {
                    try {
                        s.updateEvent(e);
                        done[0]++;
                    } catch (Throwable x) {
                        failures.add("events: " + x);
                    }
                }
            }
        });
        Thread readers = daemon("j2-strategy-readers", () -> {
            for (int i = 0; i < 20000; i++) {
                try {
                    s.active();
                    s.isActive((long) (i % 40));
                    done[1]++;
                } catch (Throwable x) {
                    failures.add("readers: " + x);
                }
            }
        });
        Thread resets = daemon("j2-strategy-resets", () -> {
            for (int i = 0; i < 500; i++) {
                try {
                    s.resetIteration(i);
                    done[2]++;
                } catch (Throwable x) {
                    failures.add("resets: " + x);
                }
            }
        });
        for (Thread x : List.of(events, readers, resets)) {
            x.start();
        }
        long until = System.nanoTime() + 30_000_000_000L;
        for (Thread x : List.of(events, readers, resets)) {
            x.join(Math.max(1, (until - System.nanoTime()) / 1_000_000));
            if (x.isAlive()) {
                failures.add(x.getName() + ": not finished in 30 s (deadlock)");
            }
        }
        // join() orders the counters before these reads (a live worker is already a failure)
        System.out.println("J2PROBE-COUNTS caller=" + callerDone[0] + " events=" + done[0] + " readers=" + done[1]
                + " resets=" + done[2]);

        // C: the package-private path (NOTE only)
        TrackTasks d = new TrackTasks();
        d.updateEvent(J2TrackerTest.start(1));
        Set<Long> sync2 = Collections.synchronizedSet(new HashSet<>(List.of(1L)));
        CountDownLatch held = new CountDownLatch(1);
        Thread c2 = daemon("j2-deadlock-caller", () -> {
            try {
                held.await();
                d.updateAndRetainActive(J2TrackerTest.start(6), sync2);
            } catch (Throwable x) {
                failures.add("deadlock caller: " + x);
            }
        });
        Thread h2 = daemon("j2-deadlock-holder", () -> {
            try {
                synchronized (sync2) {
                    held.countDown();
                    long deadline = System.nanoTime() + 10_000_000_000L;
                    while (c2.getState() != Thread.State.BLOCKED && c2.isAlive()) {
                        if (System.nanoTime() > deadline) {
                            failures.add("deadlock holder: the caller never blocked on the set");
                            return;
                        }
                        Thread.onSpinWait();
                    }
                    d.updateEvent(J2TrackerTest.start(5));
                }
            } catch (Throwable x) {
                failures.add("deadlock holder: " + x);
            }
        });
        c2.start();
        h2.start();
        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        Boolean deadlocked = null;
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (deadlocked == null && System.nanoTime() < deadline) {
            long[] ids = mx.findDeadlockedThreads();
            Set<Long> ds = new HashSet<>();
            if (ids != null) {
                for (long id : ids) {
                    ds.add(id);
                }
            }
            if (ds.contains(h2.getId()) && ds.contains(c2.getId())) {
                deadlocked = true;
            } else if (!h2.isAlive() && !c2.isAlive()) {
                deadlocked = false;
            } else {
                Thread.sleep(10);
            }
        }
        if (deadlocked == null) {
            failures.add("deadlock demo: neither a deadlock nor both threads finished within 10 s");
        }
        System.out.println("NOTE updateAndRetainActive with a synchronized foreign set held across a tracker call: "
                + (Boolean.TRUE.equals(deadlocked) ? "deadlocked (found by ThreadMXBean)" : "finished")
                + "; the strategy passes only its own HashSet");
        List<String> f = new ArrayList<>(failures);
        if (f.isEmpty()) {
            System.out.println("J2PROBE-OK deadlocked=" + deadlocked);
            return 0;
        }
        System.out.println("J2PROBE-FAIL " + (f.size() > 10 ? f.size() + " failures, first 10: " + f.subList(0, 10) : f));
        return 1;
    }
}
