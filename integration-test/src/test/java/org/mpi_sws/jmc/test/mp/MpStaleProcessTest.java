package org.mpi_sws.jmc.test.mp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.annotations.JmcIgnoreInstrumentation;
import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.util.statements.JmcAssume;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils;
import org.mpi_sws.jmc.strategies.must.MustStrategy;
import org.mpi_sws.jmc.test.mp.MpTestMessages.Ping;
import org.mpi_sws.jmc.test.mp.MpTestMessages.Pong;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/**
 * CI1 (plan/jmc-must/log/CI1.report.md §2): a process thread that is still paused when a run ends with
 * an error must not report into the next run in the same JVM.
 *
 * <p>Each sequence runs, in one JVM and one after another: three checks of {@link #failing()}, a
 * program whose run fails while one or more processes ({@code sleeper0..}, parked on a receive nobody
 * answers, or not yet started) are still paused; then one check of {@link #pingPong()}, a fault-free round trip.
 * At the end of each failing run the runtime's task manager wakes the paused sleepers with
 * {@code null}; before the CI1 fix such a thread could run on into the next check and raise its events
 * there (a wrong error, a lost task, a hang). Derived outcomes (tester, before running): every failing
 * check fails with the deliberate failure's own message; every ping-pong check completes with no
 * blocked iteration ({@code mp-random}: the full budget; {@code must}: one graph, exhausted). Each
 * check is bounded, so a hang is a failure, not a CI timeout.
 *
 * <p>Both strategies advance {@code MpThread}'s iteration counter first thing in
 * {@code resetIteration}: {@code MustStrategy} and, since the owner's agreement Q-CI1-1,
 * {@code MpRandomSchedulingStrategy}. Removing either line made the matching variant red in most
 * runs (CI1 rounds 01-02: {@code must} 9 of 9, {@code mp-random} 2 of 3).
 *
 * <p>The failing programs are per strategy, chosen by measurement without the fix (CI1 round-01
 * report): under {@code mp-random} both the latched-fault shape of the CI hang with the CI test's
 * seeds and a one-sleeper failing process (each alone was red in only about a third to a half of the
 * runs); under {@code must} four sleepers and a failing process (red in 9 of 9 runs with
 * {@code MustStrategy}'s counter removed; one sleeper gave 1 of 5). The test is a regression net
 * with a high, not a certain, detection rate; a run is green only if every sequence behaves.
 */
@JmcIgnoreInstrumentation
public class MpStaleProcessTest {

    private static final String REPORT_PATH = "build/test-results/jmc-report";
    private static final String FAILURE = "CI1 deliberate failure";
    private static final int SEQUENCES = 30;
    private static final int FAILING_RUNS = 3;
    private static final int PING_ITERATIONS = 5;
    private static final long BOUND_SECONDS = 30;

    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ci1-check");
        t.setDaemon(true);
        return t;
    });

    /** {@code sleepers} processes parked (or not yet started) when the awaited failer's failure fails the run. */
    static void failing(int sleepers) {
        for (int i = 0; i < sleepers; i++) {
            MpProcess.spawn("sleeper" + i, () -> Channel.recv(CommunicationModel.ASYN));
        }
        MpProcess.Handle failer = MpProcess.spawn("failer", () -> {
            throw new IllegalStateException(FAILURE);
        });
        failer.await();
    }

    private static final String GUARD_FAULT = "instrumented access";

    /**
     * The latched-fault shape of {@code MpBlockingAndGuardTest.latchedFault_...} (the CI hang): the
     * receiver's guard re-enters the runtime when the sender's message arrives, the fault fails the
     * run on main's await, and the receiver is still parked. As there, {@code assume(false)} after the
     * await prunes an iteration in which the fault was not delivered, and the CI test's three seeds and
     * five iterations are used, under which that test asserts the run fails with the guard's fault.
     */
    static void latchedFault() {
        MpProcess.Handle receiver = MpProcess.spawn("receiver", () -> Channel.recv(CommunicationModel.ASYN,
                instrumentedReadGuard()));
        MpProcess.Handle sender = MpProcess.spawn("sender", () -> Channel.send(CommunicationModel.ASYN,
                receiver.tid(), new MpTestMessages.Val(1)));
        sender.await();
        JmcAssume.assume(false);
    }

    /** {@code MpBlockingAndGuardTest.FAULT_SEEDS}. */
    private static final long[] FAULT_SEEDS = {20260910L, 1234567890123L, 42424242L};

    /** As {@code MpBlockingAndGuardTest.instrumentedReadGuard}: a guard performing an instrumented read. */
    private static Predicate<MpMessage> instrumentedReadGuard() {
        return m -> {
            JmcRuntimeUtils.readEvent("org/mpi_sws/jmc/test/mp/MpStaleProcessTest", "currentTerm", "I", null);
            return true;
        };
    }

    /** A round trip that always completes: main pings a responder, which answers, and main awaits it. */
    static void pingPong() {
        final Tid main = MpProcess.self();
        MpProcess.Handle responder = MpProcess.spawn("responder", () -> {
            Ping p = Channel.recv(CommunicationModel.ASYN, Ping.class);
            Channel.send(CommunicationModel.ASYN, p.from(), new Pong(p.v() + 1));
        });
        Channel.send(CommunicationModel.ASYN, responder.tid(), new Ping(main, 41));
        Pong reply = Channel.recv(CommunicationModel.ASYN, Pong.class);
        if (reply.v() != 42) {
            throw new IllegalStateException("wrong reply " + reply.v());
        }
        responder.await();
    }

    interface Checker {
        JmcModelCheckerReport check(String name, int iterations, long seed, JmcFunctionalTestTarget.Target p)
                throws JmcCheckerException;
    }

    /** The outcome of one bounded check: the report, or the failure's message chain. */
    record Outcome(JmcModelCheckerReport report, String failure) {}

    static Outcome bounded(Checker c, String name, int iterations, long seed, JmcFunctionalTestTarget.Target p) {
        Future<JmcModelCheckerReport> f = POOL.submit(() -> c.check(name, iterations, seed, p));
        try {
            return new Outcome(f.get(BOUND_SECONDS, TimeUnit.SECONDS), null);
        } catch (TimeoutException e) {
            f.cancel(true);
            fail(name + " (seed " + seed + "): the check did not end within " + BOUND_SECONDS + " s (hang)");
            return null;
        } catch (ExecutionException e) {
            return new Outcome(null, chain(e.getCause()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
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

    static JmcModelCheckerReport mpRandom(String name, int iterations, long seed, JmcFunctionalTestTarget.Target p)
            throws JmcCheckerException {
        return new JmcModelChecker(new JmcCheckerConfiguration.Builder().strategyType("mp-random")
                .numIterations(iterations).seed(seed).reportPath(REPORT_PATH).build())
                .check(new JmcFunctionalTestTarget(name, p));
    }

    static JmcModelCheckerReport must(String name, int iterations, long seed, JmcFunctionalTestTarget.Target p)
            throws JmcCheckerException {
        MustStrategy s = new MustStrategy(seed, REPORT_PATH);
        return new JmcModelChecker(new JmcCheckerConfiguration.Builder().strategyType("must")
                .strategyConstructor(c -> s).numIterations(iterations).seed(seed).reportPath(REPORT_PATH).build())
                .check(new JmcFunctionalTestTarget(name, p));
    }

    /** Runs the sequences; returns every deviation from the derived outcomes. */
    static List<String> sequences(Checker c, JmcFunctionalTestTarget.Target failing, String expected,
            long[] seeds, int failingIterations, int pingIterations, int pingTotal) {
        List<String> bad = new ArrayList<>();
        for (int s = 0; s < SEQUENCES; s++) {
            for (int k = 0; k < FAILING_RUNS; k++) {
                long seed = seeds != null ? seeds[k] : 1000L * s + k;
                Outcome o = bounded(c, "ci1-failing", failingIterations, seed, failing);
                if (o.failure() == null) {
                    bad.add("sequence " + s + " failing run " + k + ": did not fail");
                } else if (!o.failure().contains(expected)) {
                    bad.add("sequence " + s + " failing run " + k + ": wrong failure: " + o.failure());
                }
            }
            Outcome o = bounded(c, "ci1-pingpong", pingIterations, 7000L + s, MpStaleProcessTest::pingPong);
            if (o.failure() != null) {
                bad.add("sequence " + s + " ping-pong: failed: " + o.failure());
            } else if (o.report().getTotalIterations() != pingTotal || o.report().getBlockedIterations() != 0) {
                bad.add("sequence " + s + " ping-pong: total " + o.report().getTotalIterations() + " blocked "
                        + o.report().getBlockedIterations() + ", expected " + pingTotal + " and 0");
            }
        }
        System.out.println("CI1 deviations: " + bad);
        return bad;
    }

    /** Needs {@code MpRandomSchedulingStrategy}'s counter line (Q-CI1-1); red without it. */
    @Test
    public void staleProcess_mpRandom_doesNotReachTheNextRun() {
        List<String> bad = new ArrayList<>(sequences(MpStaleProcessTest::mpRandom, MpStaleProcessTest::latchedFault,
                GUARD_FAULT, FAULT_SEEDS, 5, PING_ITERATIONS, PING_ITERATIONS));
        bad.addAll(sequences(MpStaleProcessTest::mpRandom, () -> failing(1), FAILURE, null, 3, PING_ITERATIONS,
                PING_ITERATIONS));
        assertTrue(bad.isEmpty(), "deviations from the derived outcomes: " + bad);
    }

    /** {@code MustStrategy}'s counter line; one graph for the ping-pong. */
    @Test
    public void staleProcess_must_doesNotReachTheNextRun() {
        List<String> bad = sequences(MpStaleProcessTest::must, () -> failing(4), FAILURE, null, 3, 100, 1);
        assertEquals(List.of(), bad, "deviations from the derived outcomes");
    }
}
