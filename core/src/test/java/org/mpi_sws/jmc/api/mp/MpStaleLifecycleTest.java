package org.mpi_sws.jmc.api.mp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tester tests for J7a round 03 (plan/jmc-must/log/dev/CI1/round-03.prompt.md): deterministic coverage
 * of Codex's J7a round-01 majors. In {@code api.mp}'s package for {@link MpThread#daemonsCreated()}.
 *
 * <p>Every test first runs a <b>holding run</b> (mp-random, one iteration) that fails while a process,
 * {@code holder}, is parked inside the runtime: main receives the holder's "ready", then awaits a
 * process that throws {@link #FAILURE}. The holder's body is {@code try { send ready; recv } finally
 * { ... }}, so after the run its thread is a stale thread whose {@code finally} the test controls with
 * latches. A second run in the same JVM then shows what the stale thread can and cannot do.
 *
 * <p>M1 (the barrier): the holder is suspended while parked in the runtime's wait, so it can only
 * remove its task's future (by id) after the test resumes it; without the barrier, the next run reuses
 * that id first. M2 (stale unwinding): the holder's {@code finally} calls into {@code api.mp} after the
 * next run started; every such call must end the thread (with {@link MpThread.Stale}) before touching
 * the runtime or the package's shared state. Derived outcomes are in the CI1 round-03 report. Every wait
 * is bounded, so a hang fails the test.
 */
class MpStaleLifecycleTest {

    private static final String REPORT_PATH = "build/test-results/ci1-r3";
    private static final String FAILURE = "CI1 r3 deliberate failure";
    private static final CommunicationModel M = CommunicationModel.ASYN;

    record Ready(int v) implements MpMessage {}

    record Never(int v) implements MpMessage {}

    record Ping(Tid from, int v) implements MpMessage {}

    record Pong(int v) implements MpMessage {}

    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ci1-r3-check");
        t.setDaemon(true);
        return t;
    });

    /** What the test controls and observes about the holder. */
    static final class Hold {
        volatile Thread thread;
        final CountDownLatch inFinally = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        volatile Runnable action = () -> {};
        volatile boolean actionReturned;
        volatile Throwable actionThrew;
        volatile MpProcess.Handle other;
    }

    record Outcome(JmcModelCheckerReport report, String failure) {}

    static Future<JmcModelCheckerReport> start(String name, int iterations, long seed, Runnable body) {
        return POOL.submit(() -> new JmcModelChecker(new JmcCheckerConfiguration.Builder()
                .strategyType("mp-random").numIterations(iterations).seed(seed).reportPath(REPORT_PATH).build())
                .check(new JmcFunctionalTestTarget(name, body::run)));
    }

    static Outcome await(Future<JmcModelCheckerReport> f, long seconds) {
        try {
            return new Outcome(f.get(seconds, TimeUnit.SECONDS), null);
        } catch (TimeoutException e) {
            return new Outcome(null, "TIMEOUT after " + seconds + " s (hang)");
        } catch (ExecutionException e) {
            return new Outcome(null, chain(e.getCause()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Outcome(null, "interrupted");
        }
    }

    static String chain(Throwable thrown) {
        StringBuilder out = new StringBuilder();
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            out.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append(" | ");
            if (t.getCause() == t) {
                break;
            }
        }
        return out.toString();
    }

    static void awaitQuietly(CountDownLatch l) {
        try {
            l.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("removal")
    static void suspendWhenParked(Thread t) {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (t.getState() != Thread.State.WAITING) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("holder never parked: " + t.getState());
            }
            Thread.onSpinWait();
        }
        t.suspend();
    }

    /** The holding run's main body. */
    static Runnable holding(Hold h, boolean suspend) {
        return () -> {
            final Tid main = MpProcess.self();
            MpProcess.spawn("holder", () -> {
                h.thread = Thread.currentThread();
                try {
                    Channel.send(M, main, new Ready(0));
                    Channel.recv(M, Never.class);
                } finally {
                    h.inFinally.countDown();
                    awaitQuietly(h.release);
                    try {
                        h.action.run();
                        h.actionReturned = true;
                    } catch (RuntimeException | Error e) {
                        h.actionThrew = e;
                        throw e;
                    }
                }
            });
            Channel.recv(M, Ready.class);
            if (suspend) {
                suspendWhenParked(h.thread);
            }
            MpProcess.Handle failer = MpProcess.spawn("failer", () -> {
                throw new IllegalStateException(FAILURE);
            });
            h.other = failer;
            failer.await();
        };
    }

    /** A round trip that always completes: main pings a responder, which answers, and main awaits it. */
    static void pingPong() {
        final Tid main = MpProcess.self();
        MpProcess.Handle responder = MpProcess.spawn("responder", () -> {
            Ping p = Channel.recv(M, Ping.class);
            Channel.send(M, p.from(), new Pong(p.v() + 1));
        });
        Channel.send(M, responder.tid(), new Ping(main, 41));
        Pong reply = Channel.recv(M, Pong.class);
        if (reply.v() != 42) {
            throw new IllegalStateException("wrong reply " + reply.v());
        }
        responder.await();
    }

    static void holdingRunFails(Hold h, boolean suspend) {
        Outcome o = await(start("ci1-r3-holding", 1, 7L, holding(h, suspend)), 60);
        assertTrue(o.failure() != null && o.failure().contains(FAILURE),
                "the holding run must fail with the deliberate failure: " + o);
    }

    // ------------------------------------------------------------------ M1: the barrier

    /**
     * M1 (a): the holder is suspended inside the runtime's wait when the holding run ends, so its
     * thread is alive; the next run's main reaches {@code point} only after creating {@code responder}
     * (task 2, the holder's id). Derived with the barrier: the creation waits, {@code point} is not
     * reached while the holder is suspended; after the resume the holder removes its own future (no
     * task 2 exists yet), exits silently, and the run completes (1 iteration, 0 blocked). Without the
     * barrier: {@code point} is reached at once, task 2 is parked, the resumed holder removes task 2's
     * future, and the run fails ("Resuming a non existent task") or hangs.
     */
    @Test
    @SuppressWarnings("removal")
    void m1_barrier_newRunWaitsForTheStaleThread_thenCompletes() throws Exception {
        Hold h = new Hold();
        h.release.countDown();
        holdingRunFails(h, true);
        assertTrue(h.thread.isAlive(), "the suspended holder is alive");
        CountDownLatch point = new CountDownLatch(1);
        CountDownLatch go = new CountDownLatch(1);
        Future<JmcModelCheckerReport> run2 = start("ci1-r3-next", 1, 11L, () -> {
            final Tid main = MpProcess.self();
            MpProcess.Handle responder = MpProcess.spawn("responder", () -> {
                Ping p = Channel.recv(M, Ping.class);
                Channel.send(M, p.from(), new Pong(p.v() + 1));
            });
            point.countDown();
            awaitQuietly(go);
            Channel.send(M, responder.tid(), new Ping(main, 41));
            Channel.recv(M, Pong.class);
            responder.await();
        });
        List<String> seen = new ArrayList<>();
        boolean earlyPoint = point.await(3, TimeUnit.SECONDS);
        seen.add("point reached while the holder was suspended: " + earlyPoint);
        h.thread.resume();
        h.thread.join(10_000L);
        seen.add("holder alive after resume: " + h.thread.isAlive());
        seen.add("point reached after resume: " + point.await(10, TimeUnit.SECONDS));
        go.countDown();
        Outcome o = await(run2, 30);
        seen.add("next run: " + (o.failure() != null ? o.failure()
                : "total " + o.report().getTotalIterations() + " blocked " + o.report().getBlockedIterations()));
        System.out.println("CI1-R3 m1a " + seen);
        assertEquals(List.of("point reached while the holder was suspended: false", "holder alive after resume: false",
                "point reached after resume: true", "next run: total 1 blocked 0"), seen);
    }

    /**
     * M1 (b): the holder's {@code finally} never returns (its latch is released only at the end), so a
     * thread of an earlier iteration stays alive; the next run's first process creation fails after the
     * barrier's bound (30 s) with the message naming the holder's task id 2.
     */
    @Test
    void m1_barrier_failsLoudlyWhenTheStaleThreadNeverEnds() throws Exception {
        Hold h = new Hold();
        try {
            holdingRunFails(h, false);
            assertTrue(h.inFinally.await(10, TimeUnit.SECONDS), "the stale holder is in its finally");
            Outcome o = await(start("ci1-r3-next", 1, 11L, MpStaleLifecycleTest::pingPong), 60);
            System.out.println("CI1-R3 m1b " + o);
            assertTrue(o.failure() != null && o.failure().contains("process thread(s) [2] of an earlier iteration"),
                    "the next run must fail naming the stale thread: " + o);
        } finally {
            h.release.countDown();
        }
    }

    // ------------------------------------------------------------------ M2: stale unwinding

    /**
     * M2: the holder's {@code finally} runs {@code action} while the next run's main is running (before
     * it creates any process). Derived: the action throws {@link MpThread.Stale} before touching the
     * runtime, the holder's thread ends, and the next run completes (3 iterations, 0 blocked).
     */
    static List<String> staleAction(Hold h) throws Exception {
        holdingRunFails(h, false);
        assertTrue(h.inFinally.await(10, TimeUnit.SECONDS), "the stale holder is in its finally");
        CountDownLatch point = new CountDownLatch(1);
        CountDownLatch go = new CountDownLatch(1);
        Future<JmcModelCheckerReport> run2 = start("ci1-r3-next", 3, 11L, () -> {
            point.countDown();
            awaitQuietly(go);
            pingPong();
        });
        List<String> seen = new ArrayList<>();
        seen.add("next run's main reached the point: " + point.await(10, TimeUnit.SECONDS));
        h.release.countDown();
        h.thread.join(10_000L);
        seen.add("holder alive: " + h.thread.isAlive());
        seen.add("action returned: " + h.actionReturned);
        seen.add("action threw: " + (h.actionThrew == null ? null : h.actionThrew.getClass().getSimpleName()));
        go.countDown();
        Outcome o = await(run2, 30);
        seen.add("next run: " + (o.failure() != null ? o.failure()
                : "total " + o.report().getTotalIterations() + " blocked " + o.report().getBlockedIterations()));
        System.out.println("CI1-R3 m2 " + seen);
        return seen;
    }

    static final List<String> DERIVED = List.of("next run's main reached the point: true", "holder alive: false",
            "action returned: false", "action threw: Stale", "next run: total 3 blocked 0");

    @Test
    void m2_staleFinally_await() throws Exception {
        Hold h = new Hold();
        h.action = () -> h.other.await();
        assertEquals(DERIVED, staleAction(h));
    }

    @Test
    void m2_staleFinally_constructProcess() throws Exception {
        Hold h = new Hold();
        h.action = () -> new MpThread(() -> {}, "late");
        assertEquals(DERIVED, staleAction(h));
    }

    @Test
    void m2_staleFinally_constructDaemon_daemonCountUnchanged() throws Exception {
        Hold h = new Hold();
        int[] before = {-1};
        h.action = () -> {
            before[0] = MpThread.daemonsCreated();
            new MpThread(() -> {}, true);
        };
        List<String> seen = new ArrayList<>(staleAction(h));
        seen.add("daemon count changed: " + (MpThread.daemonsCreated() != before[0]));
        List<String> derived = new ArrayList<>(DERIVED);
        derived.add("daemon count changed: false");
        assertEquals(derived, seen);
    }

    @Test
    void m2_staleFinally_send() throws Exception {
        Hold h = new Hold();
        h.action = () -> Channel.send(M, Tid.of(1L), new Ready(9));
        assertEquals(DERIVED, staleAction(h));
    }

    @Test
    void m2_staleFinally_recv() throws Exception {
        Hold h = new Hold();
        h.action = () -> Channel.recv(M);
        assertEquals(DERIVED, staleAction(h));
    }

    @Test
    void m2_staleFinally_nondet() throws Exception {
        Hold h = new Hold();
        h.action = () -> Nondet.chooseBoolean();
        assertEquals(DERIVED, staleAction(h));
    }

    /** M2: {@code runMain} is main-only; a process calling it fails, and so does the run. */
    @Test
    void m2_runMainOnProcessThread_throws() {
        Outcome o = await(start("ci1-r3-runmain", 1, 7L, () -> {
            MpProcess.Handle p = MpProcess.spawn("p", () -> MpProcess.runMain(() -> {}));
            p.await();
        }), 60);
        System.out.println("CI1-R3 runMain " + o);
        assertTrue(o.failure() != null && o.failure().contains("MpProcess.runMain is for the program's main task"),
                "a process calling runMain must fail the run: " + o);
    }

    // ------------------------------------------------------------------ round 04 (Codex J7a round 02)

    /**
     * Codex round-02 M1: a failed {@code start()} (a finished process started again) must not leave an
     * {@code ALIVE} entry. The first run's main starts {@code p}, awaits it, then starts it again: the
     * JVM refuses ({@code IllegalThreadStateException}) and the run fails with it. Derived: the next
     * run's process creation neither waits nor fails, so a ping-pong completes (1 iteration, 0 blocked)
     * well inside 15 s; with the leak it would wait 30 s and fail naming the process.
     */
    @Test
    void r4_failedStart_leavesNoAliveEntry() {
        Outcome first = await(start("ci1-r4-restart", 1, 7L, () -> {
            MpThread p = new MpThread(() -> {}, "p");
            p.start();
            try {
                p.join1();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            p.start();
        }), 60);
        System.out.println("CI1-R4 restart " + first);
        assertTrue(first.failure() != null && first.failure().contains("IllegalThreadStateException"),
                "starting a finished process again must fail the run: " + first);
        long t0 = System.currentTimeMillis();
        Outcome next = await(start("ci1-r4-next", 1, 11L, MpStaleLifecycleTest::pingPong), 15);
        long ms = System.currentTimeMillis() - t0;
        System.out.println("CI1-R4 next after restart " + next + " in " + ms + " ms");
        assertTrue(next.failure() == null && next.report().getTotalIterations() == 1
                && next.report().getBlockedIterations() == 0, "the next run must complete at once: " + next);
    }

    /**
     * Codex round-02 m1: between runs (no current task) the stale holder's {@code finally} calls
     * {@code action}; derived: it ends through {@link MpThread.Stale}, not "No current task".
     */
    static String betweenRuns(Runnable action) throws Exception {
        Hold h = new Hold();
        h.action = action;
        holdingRunFails(h, false);
        assertTrue(h.inFinally.await(10, TimeUnit.SECONDS), "the stale holder is in its finally");
        h.release.countDown();
        h.thread.join(10_000L);
        assertTrue(!h.thread.isAlive(), "the holder ended");
        String seen = h.actionReturned ? "returned"
                : h.actionThrew == null ? "nothing" : h.actionThrew.getClass().getSimpleName() + ": "
                        + h.actionThrew.getMessage();
        System.out.println("CI1-R4 between runs " + seen);
        return seen;
    }

    @Test
    void r4_noCurrentTask_self_endsThroughStale() throws Exception {
        assertTrue(betweenRuns(() -> MpProcess.self()).startsWith("Stale: "));
    }

    @Test
    void r4_noCurrentTask_recv_endsThroughStale() throws Exception {
        assertTrue(betweenRuns(() -> Channel.recv(M)).startsWith("Stale: "));
    }

    @Test
    void r4_noCurrentTask_nondet_endsThroughStale() throws Exception {
        assertTrue(betweenRuns(() -> Nondet.chooseBoolean()).startsWith("Stale: "));
    }
}
