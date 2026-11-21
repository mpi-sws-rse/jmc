package org.mpi_sws.jmc.test.mp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.annotations.JmcIgnoreInstrumentation;
import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
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
import org.mpi_sws.jmc.runtime.JmcUnsupportedEventException;
import org.mpi_sws.jmc.runtime.scheduling.MpDeliveryValue;
import org.mpi_sws.jmc.runtime.scheduling.MpDeliveryValueAdapter;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;
import org.mpi_sws.jmc.strategies.mp.MpRandomSchedulingStrategy;
import org.mpi_sws.jmc.test.mp.MpTestMessages.Ping;
import org.mpi_sws.jmc.test.mp.MpTestMessages.Pong;
import org.mpi_sws.jmc.test.mp.MpTestMessages.Val;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * What happens when a route-A program <em>cannot</em> go on: blocking (Must's {@code B} label) and
 * the one thing a receive guard may not do.
 *
 * <p>Every claim here is a finding of the S3 review — rounds 1 and 2 — on
 * {@code org.mpi_sws.jmc.strategies.mp.MpRandomSchedulingStrategy}. Round 1 first:
 *
 * <ul>
 *   <li><b>B1</b> — the parked receivers of an iteration that can no longer progress are blocked in
 *       <b>descending task id</b>, main last, so that every spawned process unwinds through
 *       {@code JmcThread.run}'s {@code finally} ({@code FINISH_EVENT}, join) before the iteration
 *       ends. Blocking main first — {@code parked.firstKey()}, which the reviewer found — ends the
 *       iteration in {@code JmcModelChecker.check} while the other parked tasks are still paused on
 *       their futures; {@code TaskManager.reset()} then completes those futures with {@code null},
 *       each task resumes inside {@code Channel.request}, throws, and pushes a late
 *       {@code FINISH_EVENT} and a stray {@code enable()} into the <em>next</em> iteration. The
 *       three shapes below are the three the reviewer's required change names.</li>
 *   <li><b>M1</b> — an iteration in which anything was blocked is counted as blocked in the report,
 *       so {@code total − blocked} (what {@code @JmcExpectExecutions} compares against) sees it.
 *       Before the fix a fully blocked run reported "all iterations completed".</li>
 *   <li><b>M2</b> — a guard that performs an instrumented access re-enters the runtime from inside
 *       {@code SchedulingStrategy.updateEvent}. That is forbidden (contract §2.2, A6) and is
 *       detected rather than tolerated.</li>
 * </ul>
 *
 * <p>Round 2 measured what round 1's fixes still let through, and added four more:
 *
 * <ul>
 *   <li><b>M1</b> (round 2) — the latched guard fault was <em>dropped</em> when main was the parked
 *       receiver: main was blocked, {@code JmcModelChecker.check}'s {@code HaltTaskException} path
 *       emits no {@code mainEndEvent}, {@code resetIteration} cleared the latch, and the run passed
 *       reporting blocked iterations. Now main is resumed with an {@code MpFaultValue} instead
 *       (contract §3.2(d)) and a faulted iteration is not counted as blocked.</li>
 *   <li><b>M2</b> (round 2) — a normal iteration could still end with a task paused: main returning
 *       without awaiting a spawned process. Not blocked but <b>abandoned</b>, and measured as a hang
 *       6/6. The strategy now refuses it at main's {@code FINISH_EVENT}, naming the processes; the
 *       rule is "started and not finished", not "parked".</li>
 *   <li><b>m1</b> (round 2) — the {@code ReplayPlaceholder} refusal in {@code Channel.request} sits
 *       on the path of every receive and had no test in either direction, which is exactly how
 *       finding F-F got in.</li>
 *   <li><b>n1</b> (round 2) — {@code blockedIterations} must be per check, not per strategy
 *       instance.</li>
 * </ul>
 *
 * <p>Round 3 measured the other direction of the latch and added one more:
 *
 * <ul>
 *   <li><b>M1</b> (round 3) — the latch was cleared on <em>delivery</em>, so a main that
 *       <em>caught</em> the delivered exception dropped it: the checker recorded nothing and the run
 *       came back green ({@code total=5 blocked=0}) over a program in which the forbidden guard had
 *       been detected in every iteration. The latch is now released only when the checker has
 *       recorded the error, and {@code resetIteration} reports a fault the report does not show —
 *       {@link #mainCatchingTheDeliveredFault_isStillFailedAtTheEndOfTheIteration}.</li>
 * </ul>
 *
 * <h2>How a leaked task is made observable</h2>
 *
 * <p>A task left paused when the iteration ends is woken with {@code null} at
 * {@code TaskManager.reset()}, and {@code Channel.request}'s unwrap turns that into a
 * {@link JmcUnsupportedEventException} ("no MpDeliveryValue came back; got null") <em>inside the
 * process body</em>. Each spawned receive below therefore catches <b>that type only</b> and counts
 * it: the legitimate {@code HaltTaskException} of a blocked receive is not caught and still ends the
 * body. A leaked task is exactly a non-zero count, so {@code wokeWithNull == 0} is the assertion
 * that the block order left nobody behind.
 *
 * <h2>Why this class is programmatic and uninstrumented</h2>
 *
 * <p>The claims are about the <em>report</em> — {@code getTotalIterations()} and
 * {@code getBlockedIterations()} — which only the programmatic {@code JmcModelChecker.check} hands
 * back, and about an exception {@code check} must throw. Both are read after {@code check} has
 * returned and {@code JmcRuntime.tearDown} has run, where an instrumented field access would park
 * the JUnit thread on a future nothing will complete (S3 report, F-B). Hence
 * {@link JmcIgnoreInstrumentation}, exactly as in {@code MpApiWiringSnapshotTest} and
 * {@code MpApiStrategySupportTest}; the agent is exercised on instrumented programs by
 * {@code MpApiWiringTest}. Nothing is lost: these programs use only
 * {@code org.mpi_sws.jmc.api.mp}, which reports its own events by calling the runtime directly.
 *
 * <p>Recording follows the same rule as the other route-A classes: a spawned body's exceptions are
 * only logged ({@code JmcThread.run}) and an {@code AssertionError} escapes even that catch to the
 * uncaught-exception handler, so <b>every</b> body here only writes into a {@code final} array
 * captured from the test method, and every assertion is made by JUnit after {@code check} returns.
 */
@JmcIgnoreInstrumentation
public class MpBlockingAndGuardTest {

    /** The seed, fixed so a failure is reproducible. */
    private static final long SEED = 20260910L;

    /** Where the checker writes its report; the default of {@code JmcCheckerConfiguration}. */
    private static final String REPORT_PATH = "build/test-results/jmc-report";

    /**
     * The iteration budget of the accounting tests. Ten is the minimum the review's required change
     * names, and it is enough for the block order to be exercised against many schedules: which
     * parked task is blocked first is decided by the strategy, but which tasks are parked when the
     * run stalls depends on the sampled schedule, so a single iteration would prove little.
     */
    private static final int ITERATIONS = 10;

    /**
     * Runs {@code program} under {@code "mp-random"} and returns the report.
     *
     * @param name the target's display name
     * @param iterations the iteration budget
     * @param program the program under test
     * @return the checker's report — {@code getTotalIterations()} and {@code getBlockedIterations()}
     *     are what the accounting claims are made about
     * @throws JmcCheckerException whatever the check fails with
     */
    private static JmcModelCheckerReport run(
            String name, int iterations, JmcFunctionalTestTarget.Target program)
            throws JmcCheckerException {
        return run(name, iterations, SEED, program);
    }

    /**
     * Runs {@code program} under {@code "mp-random"} with an explicit seed and returns the report.
     *
     * <p>The seed is a parameter because the round-2 verdict measured both of its majors as
     * seed-dependent — M1 passed on four of five seeds, M2 produced three different outcomes over
     * six — so a single fixed seed is not evidence for either. Each call is a separate {@code check},
     * hence a separate run: the strategy is constructed by {@code toRuntimeConfiguration()} from
     * this configuration.
     *
     * @param name the target's display name
     * @param iterations the iteration budget
     * @param seed the random seed
     * @param program the program under test
     * @return the checker's report
     * @throws JmcCheckerException whatever the check fails with
     */
    private static JmcModelCheckerReport run(
            String name, int iterations, long seed, JmcFunctionalTestTarget.Target program)
            throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .strategyType("mp-random")
                        .numIterations(iterations)
                        .seed(seed)
                        .reportPath(REPORT_PATH)
                        .build();
        return new JmcModelChecker(config).check(new JmcFunctionalTestTarget(name, program));
    }

    /**
     * A checker driving {@code strategy} — the {@code strategyConstructor} form, used where the
     * claim is about the strategy <em>instance</em> rather than about the report: the blocked-count
     * accessor after a failed run (M1), the placeholder injection (m1), and the reuse of one
     * instance across two checks (n1). {@code MpApiWiringSnapshotTest} uses the same hook.
     *
     * @param strategy the strategy instance to run under
     * @param iterations the iteration budget
     * @return the checker
     * @throws JmcCheckerException if the configuration is rejected
     */
    private static JmcModelChecker checkerWith(MpRandomSchedulingStrategy strategy, int iterations)
            throws JmcCheckerException {
        return new JmcModelChecker(
                new JmcCheckerConfiguration.Builder()
                        .numIterations(iterations)
                        .reportPath(REPORT_PATH)
                        .strategyConstructor(config -> strategy)
                        .build());
    }

    // ------------------------------------------------------------------ B1: multi-task parking

    /**
     * The shape the reviewer's blocker was found on: <b>main and one spawned process both parked</b>
     * on a receive nobody will ever answer.
     *
     * <p>{@code parked} is a {@code TreeMap} keyed by task id and main is always id 1, so blocking
     * {@code firstKey()} blocks main — which ends the iteration immediately (the checker catches
     * main's {@code HaltTaskException}) and leaves the child paused on its future forever. Blocking
     * {@code lastKey()} instead unwinds the child first, whose {@code FINISH_EVENT} and join are
     * reported inside the iteration that owns them, and only then blocks main.
     *
     * <p>Six assertions, each a separate way for the old order to show: the body ran once per
     * iteration; the run terminated with the full budget reported; every iteration was counted
     * blocked; neither receive ever returned; and no body was woken with {@code null}. On the
     * round-1 code this method would trip the {@code wokeWithNull} assertion (the child wakes at
     * reset inside {@code Channel.request} and throws) — and, before that, would very likely not
     * reach the assertions at all: the reviewer measured 6 of 6 runs of this exact program hanging
     * or failing with {@code No current task}, which here is a killed run under
     * {@code PER_TEST_TIMEOUT}.
     */
    @Test
    public void mainAndOneChildBothParked_terminatesWithNoTaskLeftPaused()
            throws JmcCheckerException {
        final int[] entered = {0};
        final int[] mainPassed = {0};
        final int[] childPassed = {0};
        final int[] wokeWithNull = {0};

        JmcModelCheckerReport report =
                run(
                        "mainAndOneChildBothParked",
                        ITERATIONS,
                        () -> {
                            entered[0]++;
                            MpProcess.spawn(
                                    "child",
                                    () -> {
                                        try {
                                            Channel.recv(CommunicationModel.ASYN);
                                            childPassed[0]++;
                                        } catch (JmcUnsupportedEventException e) {
                                            wokeWithNull[0]++;
                                        }
                                    });
                            Channel.recv(CommunicationModel.ASYN);
                            mainPassed[0]++;
                        });

        assertEquals(ITERATIONS, entered[0], "the program body must run once per iteration");
        assertEquals(
                ITERATIONS,
                report.getTotalIterations(),
                "the run must terminate having run the whole budget");
        assertEquals(
                ITERATIONS,
                report.getBlockedIterations(),
                "every iteration of this program is blocked (M1)");
        assertEquals(0, mainPassed[0], "main's receive must never return");
        assertEquals(0, childPassed[0], "the child's receive must never return");
        assertEquals(
                0,
                wokeWithNull[0],
                "a task was left paused and woken with null at reset (B1: block main last)");
    }

    /**
     * Two spawned processes parked while <b>main awaits them</b>: the shape in which main is not
     * parked but is not runnable either — {@code TrackTasks} removes a joining task from the active
     * set until the joined task finishes.
     *
     * <p>The strategy sees no runnable task and two parked ones and blocks them (descending task id,
     * B1). Under the owner's ruling F4 (J2b; TraceForge's {@code Block(Join)}) the awaiter of a process
     * that ended blocked blocks too: main's first {@code await()} does not return, main raises the
     * marked blocking request of {@code MpThread.awaitOfBlocked}, nothing can answer it, and main is
     * blocked in turn. So main never reaches its second await, and every iteration is blocked.
     *
     * <p>Discriminating: {@code reachedFirstAwait} is recorded just before the first await, so an
     * iteration that never got that far is distinguishable from one that blocked there;
     * {@code passedFirstAwait} and {@code mainRanOn} are 0, which a return from the await would break;
     * and neither child's receive may return, nor may any task be woken with {@code null} at reset.
     */
    @Test
    public void twoChildrenParkedWhileMainAwaits_mainBlocksAtTheFirstAwait()
            throws JmcCheckerException {
        final int[] entered = {0};
        final int[] reachedFirstAwait = {0};
        final int[] passedFirstAwait = {0};
        final int[] mainRanOn = {0};
        final int[] childPassed = {0};
        final int[] wokeWithNull = {0};

        JmcModelCheckerReport report =
                run(
                        "twoChildrenParkedWhileMainAwaits",
                        ITERATIONS,
                        () -> {
                            entered[0]++;
                            Runnable park =
                                    () -> {
                                        try {
                                            Channel.recv(CommunicationModel.ASYN);
                                            childPassed[0]++;
                                        } catch (JmcUnsupportedEventException e) {
                                            wokeWithNull[0]++;
                                        }
                                    };
                            MpProcess.Handle first = MpProcess.spawn("p1", park);
                            MpProcess.Handle second = MpProcess.spawn("p2", park);
                            reachedFirstAwait[0]++;
                            first.await();
                            passedFirstAwait[0]++;
                            second.await();
                            mainRanOn[0]++;
                        });

        assertEquals(ITERATIONS, entered[0], "the program body must run once per iteration");
        assertEquals(
                ITERATIONS,
                report.getTotalIterations(),
                "the run must terminate having run the whole budget");
        assertEquals(
                ITERATIONS,
                report.getBlockedIterations(),
                "every iteration of this program is blocked (M1)");
        assertEquals(0, childPassed[0], "no parked receive may return");
        assertEquals(ITERATIONS, reachedFirstAwait[0], "main must reach its first await in every iteration");
        assertEquals(
                0,
                passedFirstAwait[0],
                "await() of a process that ended blocked must block main too (owner's ruling F4)");
        assertEquals(0, mainRanOn[0], "main must never run past its awaits (F4)");
        assertEquals(
                0,
                wokeWithNull[0],
                "a task was left paused and woken with null at reset (B1: block main last)");
    }

    /**
     * The cross-wait: two spawned processes, each of which receives <em>before</em> sending to the
     * other, so neither send can ever happen. The classic lost-message deadlock, and the shape S5
     * must eventually report as a verdict rather than as a hang.
     *
     * <p>Both children are parked at the same time with main awaiting them, so the strategy blocks
     * 3 then 2 — and each is asserted never to have passed its receive, which is what makes this
     * different from the previous shape: here the receive is followed by a send, so a receive that
     * wrongly returned would put a message in the buffers and could unblock the other child. Zero
     * on both counters shows neither happened.
     *
     * <p>The two {@code Tid}s are exchanged through a {@code final} array written by main before the
     * children can reach the corresponding read; JMC runs one task at a time, so this is ordinary
     * sequential publication and not a race.
     */
    @Test
    public void twoChildrenCrossWait_terminatesWithNoTaskLeftPaused() throws JmcCheckerException {
        final int[] entered = {0};
        final int[] passed = {0, 0};
        final int[] wokeWithNull = {0};

        JmcModelCheckerReport report =
                run(
                        "twoChildrenCrossWait",
                        ITERATIONS,
                        () -> {
                            entered[0]++;
                            final Tid[] peer = new Tid[2];
                            MpProcess.Handle first =
                                    MpProcess.spawn(
                                            "p1",
                                            () -> {
                                                try {
                                                    Channel.recv(CommunicationModel.ASYN);
                                                    passed[0]++;
                                                    Channel.send(
                                                            CommunicationModel.ASYN,
                                                            peer[1],
                                                            new Val(1));
                                                } catch (JmcUnsupportedEventException e) {
                                                    wokeWithNull[0]++;
                                                }
                                            });
                            peer[0] = first.tid();
                            MpProcess.Handle second =
                                    MpProcess.spawn(
                                            "p2",
                                            () -> {
                                                try {
                                                    Channel.recv(CommunicationModel.ASYN);
                                                    passed[1]++;
                                                    Channel.send(
                                                            CommunicationModel.ASYN,
                                                            peer[0],
                                                            new Val(2));
                                                } catch (JmcUnsupportedEventException e) {
                                                    wokeWithNull[0]++;
                                                }
                                            });
                            peer[1] = second.tid();
                            first.await();
                            second.await();
                        });

        assertEquals(ITERATIONS, entered[0], "the program body must run once per iteration");
        assertEquals(
                ITERATIONS,
                report.getTotalIterations(),
                "the run must terminate having run the whole budget");
        assertEquals(
                ITERATIONS,
                report.getBlockedIterations(),
                "every iteration of this program is blocked (M1)");
        assertEquals(0, passed[0], "p1's receive must never return: nobody can send to it");
        assertEquals(0, passed[1], "p2's receive must never return: nobody can send to it");
        assertEquals(
                0,
                wokeWithNull[0],
                "a task was left paused and woken with null at reset (B1: block main last)");
    }

    // --------------------------------------------------------- M1: blocked-iteration accounting

    /**
     * The accounting claim in its simplest form: a program <em>every</em> iteration of which blocks
     * reports {@code blockedIterations == numIterations}.
     *
     * <p>One task, one receive, no sender — so there is nothing to interpret about which schedule
     * was sampled. Before the fix nothing ever called
     * {@code JmcModelCheckerReport.setBlockedIterations} outside {@code strategies/trust/Algo}, so
     * this run reported ten completed and zero blocked iterations and
     * {@code @JmcExpectExecutions(10)} passed on a program that never completed anything (M4).
     *
     * <p>Non-vacuous by construction: {@link #noIterationBlocked_reportsZeroBlockedIterations} is
     * the same assertion over a program that never blocks, so the counter is shown to distinguish
     * the two rather than to be a copy of {@code numIterations}.
     */
    @Test
    public void everyIterationBlocked_reportsBlockedIterationsEqualToNumIterations()
            throws JmcCheckerException {
        final int[] entered = {0};
        final int[] passed = {0};

        JmcModelCheckerReport report =
                run(
                        "everyIterationBlocked",
                        ITERATIONS,
                        () -> {
                            entered[0]++;
                            Channel.recv(CommunicationModel.ASYN);
                            passed[0]++;
                        });

        assertEquals(ITERATIONS, entered[0], "the program body must run once per iteration");
        assertEquals(ITERATIONS, report.getTotalIterations(), "every iteration ran");
        assertEquals(
                ITERATIONS,
                report.getBlockedIterations(),
                "every iteration blocked, so total − blocked is 0 completed");
        assertEquals(0, passed[0], "the receive must never return");
    }

    /**
     * The other direction: a ping that is always answered blocks nothing, so the counter stays at
     * zero and {@code total − blocked} is the full budget.
     *
     * <p>The program is a complete round trip (main pings a responder, the responder replies, main
     * reads the reply), so parking certainly happens — whichever of the two receives runs first has
     * nothing to read — but every parked receive is answered by a later send through
     * {@code retryParked}. That is the distinction the counter must make: <em>parked</em> is not
     * <em>blocked</em>, and only {@code SchedulingChoice.blockTask} counts.
     *
     * <p>Non-vacuous: the echoed value is asserted to be {@code 41 + 1} in every iteration and the
     * responder's receipt is recorded, so a run in which nothing was actually delivered — which
     * would have blocked — cannot produce these counters.
     */
    @Test
    public void noIterationBlocked_reportsZeroBlockedIterations() throws JmcCheckerException {
        final int[] entered = {0};
        final int[] replies = {0};
        final int[] responderSaw = {-1};
        final boolean[] wrongReply = {false};

        JmcModelCheckerReport report =
                run(
                        "pingAlwaysAnswered",
                        ITERATIONS,
                        () -> {
                            entered[0]++;
                            final Tid main = MpProcess.self();
                            MpProcess.Handle responder =
                                    MpProcess.spawn(
                                            "responder",
                                            () -> {
                                                Ping p =
                                                        Channel.recv(
                                                                CommunicationModel.ASYN,
                                                                Ping.class);
                                                responderSaw[0] = p.v();
                                                Channel.send(
                                                        CommunicationModel.ASYN,
                                                        p.from(),
                                                        new Pong(p.v() + 1));
                                            });
                            Channel.send(CommunicationModel.ASYN, responder.tid(), new Ping(main, 41));
                            Pong reply = Channel.recv(CommunicationModel.ASYN, Pong.class);
                            if (reply.v() != 42) {
                                wrongReply[0] = true;
                            }
                            replies[0]++;
                            responder.await();
                        });

        assertEquals(ITERATIONS, entered[0], "the program body must run once per iteration");
        assertEquals(ITERATIONS, report.getTotalIterations(), "every iteration ran");
        assertEquals(
                0,
                report.getBlockedIterations(),
                "a receive that is parked and then answered is not a blocked iteration");
        assertEquals(ITERATIONS, replies[0], "main must read a reply in every iteration");
        assertEquals(41, responderSaw[0], "the responder must have received the ping");
        assertTrue(!wrongReply[0], "main must read the reply, not the request");
    }

    /**
     * The await convention of the owner's ruling F4 (J2b, replacing S3's "a blocked process counts as
     * terminated"), pinned as a test: <b>awaiting a process that ended blocked blocks the awaiter</b>,
     * as TraceForge's {@code Block(Join)} does.
     *
     * <p>Main spawns one process that parks on a receive nobody answers, then awaits it. The
     * strategy blocks the child (main is not runnable while joining, so the child is the only parked
     * task); its body ends by the blocked receive's {@code HaltTaskException}, and main's
     * {@code await()} then raises {@code MpThread.awaitOfBlocked}'s blocking request with a guard
     * accepting nothing, which nothing can answer, so main is blocked as well and never reaches the
     * statement after the await.
     *
     * <p>Discriminating: {@code reachedAwait == ITERATIONS} shows main got to the await in every
     * iteration, {@code ranOn == 0} that the await never returned, {@code blockedIterations ==
     * ITERATIONS} that the report counts each iteration as blocked; and no task may be woken with
     * {@code null} at reset.
     */
    @Test
    public void blockedProcess_awaitBlocksTheAwaiter() throws JmcCheckerException {
        final int[] reachedAwait = {0};
        final int[] ranOn = {0};
        final int[] childPassed = {0};
        final int[] wokeWithNull = {0};

        JmcModelCheckerReport report =
                run(
                        "awaitOfABlockedProcess",
                        ITERATIONS,
                        () -> {
                            MpProcess.Handle child =
                                    MpProcess.spawn(
                                            "child",
                                            () -> {
                                                try {
                                                    Channel.recv(CommunicationModel.ASYN);
                                                    childPassed[0]++;
                                                } catch (JmcUnsupportedEventException e) {
                                                    wokeWithNull[0]++;
                                                }
                                            });
                            reachedAwait[0]++;
                            child.await();
                            ranOn[0]++;
                        });

        assertEquals(ITERATIONS, reachedAwait[0], "main must reach the await in every iteration");
        assertEquals(
                0,
                ranOn[0],
                "await() of a process that ended blocked must block main (owner's ruling F4)");
        assertEquals(
                ITERATIONS,
                report.getBlockedIterations(),
                "every iteration is blocked: the child and then main");
        assertEquals(ITERATIONS, report.getTotalIterations(), "every iteration ran");
        assertEquals(0, childPassed[0], "the child's receive must never return");
        assertEquals(0, wokeWithNull[0], "no task may be left paused (B1)");
    }

    // ------------------------------------------------------------------- M2: re-entrant guards

    /**
     * A guard that reads an instrumented field. This is what {@code JmcReadWriteVisitor} compiles
     * every non-final field read of an instrumented class into: {@code readEventWithoutYield}
     * followed by a {@code yield}. {@link JmcRuntimeUtils#readEvent} is the same pair in one call
     * ({@code updateEventAndYield}), and the detection lives in {@code updateEvent}, which both
     * reach. The parameters are the field metadata the agent would have passed.
     *
     * <p>Written as a method rather than a lambda constant so that each use is a fresh
     * {@code Predicate} — the strategy keys parked receives by task, not by guard, but a shared
     * instance would suggest state the guard does not have.
     *
     * @return a guard that re-enters the runtime the first time it is evaluated
     */
    private static Predicate<MpMessage> instrumentedReadGuard() {
        return m -> {
            JmcRuntimeUtils.readEvent(
                    "org/mpi_sws/jmc/test/mp/MpBlockingAndGuardTest", "currentTerm", "I", null);
            return true;
        };
    }

    /**
     * The paper's own selective-receive idiom — a guard that compares the message against the
     * node's current state ({@code Must.pdf} §2.3.3, the split-brain term check) — is exactly a
     * guard that reads an instrumented field, and on route A it must fail <b>loudly</b> rather than
     * corrupt the run (M2, contract §2.2 A6).
     *
     * <p>Both the receiver and the sender are spawned processes, so the guard is evaluated on a
     * <em>spawned</em> thread whichever of the two runs first: if the receiver requests first it
     * parks with an empty candidate set (the guard is never called — {@code Delivery.deliverable}
     * has nothing to test), and the sender's message re-resolves it, evaluating the guard on the
     * <b>sender's</b> thread inside {@code retryParked}; if the sender runs first the message is
     * already pending and the guard runs on the receiver's own thread. Either way the throw happens
     * where {@code JmcThread.run} would only log it. That is what the latch exists for: the fault is
     * recorded and rethrown on main's next event, so it reaches {@code JmcModelChecker} and fails
     * the check. Without the latch this program would report a green run and a corrupted buffer
     * state (the reviewer's probe: {@code Buffers.remove} failing with "not pending" some iterations
     * later).
     *
     * <p>Non-vacuous in two directions: the message is asserted to name the fault ("instrumented
     * access") rather than merely to be some exception — a hang, a null-answer unwrap or an
     * {@code IllegalArgumentException} from the buffers all fail this assertion — and
     * {@link #guardReadingOnlyItsArgument_isTheNormalPath} runs the same program shape with a
     * record-accessor guard and asserts it completes, so the detector is shown not to fire on every
     * guard.
     */
    @Test
    public void guardPerformingAnInstrumentedAccess_failsTheCheckLoudly() {
        JmcCheckerException thrown =
                assertThrows(
                        JmcCheckerException.class,
                        () ->
                                run(
                                        "reentrantGuardOnASpawnedThread",
                                        1,
                                        () -> {
                                            MpProcess.Handle receiver =
                                                    MpProcess.spawn(
                                                            "receiver",
                                                            () ->
                                                                    Channel.recv(
                                                                            CommunicationModel.ASYN,
                                                                            instrumentedReadGuard()));
                                            MpProcess.spawn(
                                                    "sender",
                                                    () ->
                                                            Channel.send(
                                                                    CommunicationModel.ASYN,
                                                                    receiver.tid(),
                                                                    new Val(1)));
                                            receiver.await();
                                        }),
                        "a guard that re-enters the runtime must fail the check");

        String chain = messageChain(thrown);
        assertTrue(
                chain.contains("instrumented access"),
                "the failure must name the re-entrant guard; message chain was: " + chain);
    }

    /**
     * The same guard with the request made by <b>main</b> and the message already pending, so the
     * guard is evaluated on the requester's own thread inside the very {@code updateEvent} that
     * carries the request.
     *
     * <p>This is the path that needs no latch: the {@code HaltExecutionException} is thrown on
     * main's thread and propagates straight out of {@code Channel.recv} and out of
     * {@code JmcModelChecker.check}. Keeping it beside the spawned-thread row is what shows the
     * detection is in the strategy and not in the latch — the two differ only in who evaluates the
     * guard.
     *
     * <p>Discriminating: {@code passedTheReceive} is asserted to have stayed at zero, so the
     * detection is shown to have stopped the receive rather than to have been reported after a value
     * was handed over.
     */
    @Test
    public void guardPerformingAnInstrumentedAccessOnTheRequestersThread_failsTheCheckLoudly() {
        final int[] passedTheReceive = {0};

        JmcCheckerException thrown =
                assertThrows(
                        JmcCheckerException.class,
                        () ->
                                run(
                                        "reentrantGuardOnTheRequestersThread",
                                        1,
                                        () -> {
                                            Channel.send(
                                                    CommunicationModel.ASYN,
                                                    MpProcess.self(),
                                                    new Val(1));
                                            Channel.recv(
                                                    CommunicationModel.ASYN,
                                                    instrumentedReadGuard());
                                            passedTheReceive[0]++;
                                        }),
                        "a guard that re-enters the runtime must fail the check");

        String chain = messageChain(thrown);
        assertTrue(
                chain.contains("instrumented access"),
                "the failure must name the re-entrant guard; message chain was: " + chain);
        assertEquals(0, passedTheReceive[0], "the receive must not have delivered anything");
    }

    /**
     * The control: a guard that reads only its argument — a record accessor — is the normal path,
     * and both of its outcomes are exercised.
     *
     * <p>One message is made pending and two receives are issued against it: the first carries a
     * guard that excludes it and must read {@code ⊥} (the {@code ∩ mval} restriction of Def. 3.4 —
     * "empty" means "nothing this receive may read", not "the buffers are empty"), the second
     * carries a guard that accepts it and must read it. So the guard is shown to be evaluated, in
     * both directions, without the detector firing.
     *
     * <p>Two further assertions make this the M2 control rather than a duplicate of the round-1
     * {@code tryRecv} row: the check completes (no {@code JmcCheckerException}), and the report
     * counts zero blocked iterations — a guard whose evaluation had been abandoned mid-resolve would
     * leave the receive parked and the iteration blocked.
     */
    @Test
    public void guardReadingOnlyItsArgument_isTheNormalPath() throws JmcCheckerException {
        final int[] entered = {0};
        final int[] excludedWasEmpty = {0};
        final int[] delivered = {0};
        final int[] wrongValue = {0};

        JmcModelCheckerReport report =
                run(
                        "recordAccessorGuard",
                        ITERATIONS,
                        () -> {
                            entered[0]++;
                            Channel.send(CommunicationModel.ASYN, MpProcess.self(), new Val(7));
                            Optional<Val> excluded =
                                    Channel.tryRecv(
                                            CommunicationModel.ASYN, Val.class, m -> m.v() == 8);
                            if (excluded.isEmpty()) {
                                excludedWasEmpty[0]++;
                            }
                            Val got =
                                    Channel.recv(CommunicationModel.ASYN, Val.class, m -> m.v() == 7);
                            if (got.v() != 7) {
                                wrongValue[0]++;
                            }
                            delivered[0]++;
                        });

        assertEquals(ITERATIONS, entered[0], "the program body must run once per iteration");
        assertEquals(
                ITERATIONS,
                excludedWasEmpty[0],
                "a guard that matches nothing pending must read ⊥ in every iteration");
        assertEquals(
                ITERATIONS,
                delivered[0],
                "a guard that matches the pending message must deliver it in every iteration");
        assertEquals(0, wrongValue[0], "the delivered message must be the one that was sent");
        assertEquals(ITERATIONS, report.getTotalIterations(), "every iteration ran");
        assertEquals(
                0,
                report.getBlockedIterations(),
                "a receive whose guard matches is answered, never blocked");
    }

    // ------------------------------------------------- M1 (round 2): the fault reaches main anyway

    /**
     * The three seeds the M1 rows are run under. The round-2 verdict measured the main-parked shape
     * as passing on four of the five seeds it tried and failing on the fifth for an unrelated
     * reason, so one seed proves nothing here: what must hold is that the run fails <em>whatever</em>
     * order the sampler picks.
     */
    private static final long[] FAULT_SEEDS = {20260910L, 1234567890123L, 42424242L};

    /**
     * The round-2 blocker in its own shape: <b>main</b> is the parked receiver when a spawned
     * sender's message re-resolves its guard and the guard faults.
     *
     * <p>The fault is raised inside {@code retryParked} on the <em>sender's</em> thread, where
     * {@code JmcThread.run} only logs it, so it is latched. The sender then unwinds and main is the
     * only task left — parked, with the matching message pending. Before the round-2 fix the
     * strategy issued {@code blockTask(1)} here: {@code Channel.recv} threw {@code HaltTaskException},
     * {@code JmcModelChecker.check} caught it and skipped {@code mainEndEvent}, {@code resetIteration}
     * cleared the latch, and the run <b>passed</b> reporting five blocked iterations — the opposite
     * of the truth, since the message was pending and the receive was never blocked at all. The fix
     * (contract §3.2(d)) resumes main with an {@code MpFaultValue} instead, and the unwrap in
     * {@code Channel.request} turns it into a {@code HaltExecutionException} on main's thread.
     *
     * <p>The sender is delayed by four {@code Nondet.chooseBoolean()} calls so that main reaches its
     * receive and parks first on most schedules; on the schedules where the message is already
     * pending the guard runs on main's own thread and throws directly, which fails the run for the
     * same reason and with the same message. Both are asserted by the same two assertions, so the
     * test does not depend on which happened.
     *
     * <p>Three discriminating assertions, on top of {@code assertThrows}: the message names the
     * fault ("instrumented access"), so a hang, a {@code No current task} or a {@code not pending}
     * buffer error all fail; main never passed its receive; and — the accounting half of the
     * finding, which is why the strategy instance is supplied rather than the strategy <em>type</em> —
     * {@code blockedIterations()} is zero, so the faulted iteration is not reported as blocked. The
     * report itself is unreachable ({@code check} throws instead of returning it), and this accessor
     * is the sanctioned substitute; it is the same number {@code teardown} writes into the report.
     */
    @Test
    public void mainParkedOnReentrantGuard_failsTheRunNotTheIteration() throws JmcCheckerException {
        for (long seed : FAULT_SEEDS) {
            final int[] passedTheReceive = {0};
            MpRandomSchedulingStrategy strategy =
                    new MpRandomSchedulingStrategy(seed, REPORT_PATH);
            JmcModelChecker checker = checkerWith(strategy, 5);

            JmcCheckerException thrown =
                    assertThrows(
                            JmcCheckerException.class,
                            () ->
                                    checker.check(
                                            new JmcFunctionalTestTarget(
                                                    "mainParkedOnAReentrantGuard",
                                                    () -> {
                                                        MpProcess.Handle sender =
                                                                MpProcess.spawn(
                                                                        "sender",
                                                                        () -> {
                                                                            for (int i = 0;
                                                                                    i < 4;
                                                                                    i++) {
                                                                                Nondet
                                                                                        .chooseBoolean();
                                                                            }
                                                                            Channel.send(
                                                                                    CommunicationModel
                                                                                            .ASYN,
                                                                                    Tid.of(1L),
                                                                                    new Val(1));
                                                                        });
                                                        Channel.recv(
                                                                CommunicationModel.ASYN,
                                                                instrumentedReadGuard());
                                                        passedTheReceive[0]++;
                                                        sender.await();
                                                    })),
                            "seed "
                                    + seed
                                    + ": a guard fault must fail the run even when main is the"
                                    + " parked receiver");

            String chain = messageChain(thrown);
            assertTrue(
                    chain.contains("instrumented access"),
                    "seed " + seed + ": the failure must name the re-entrant guard; chain: " + chain);
            assertEquals(
                    0, passedTheReceive[0], "seed " + seed + ": main's receive must not have returned");
            assertEquals(
                    0,
                    strategy.blockedIterations(),
                    "seed " + seed + ": a faulted iteration must not be counted as blocked");
        }
    }

    /**
     * A latched fault is delivered on main's <b>next event</b> — here main's join of the faulting
     * sender — and fails the run <em>before</em> main can reach a {@code JmcAssume.assume(false)}
     * that would otherwise have pruned the iteration silently.
     *
     * <p>The name is what the row proves (S3 review round 3, n1). It is <em>not</em> the tier-3
     * shape: main is never halted by the assumption, and {@code reachedAssume == 0} is the assertion
     * that says so. The verdict's tier-3 shape — a {@code HaltTaskException} from a source the
     * strategy cannot intercept, with a fault already latched, so that {@code resetIteration} is the
     * tier that delivers it — is not constructible from a program, and is covered event by event by
     * {@code MpRandomSchedulingStrategyFaultTest.tier3_resetIterationRethrowsTheFaultThatWasNeverDelivered}
     * in {@code core/src/test}.
     *
     * <p>Why not constructible: every way a program has of sequencing main after another task's
     * action — a join, a receive, a nondet — is itself an event from main, and the strategy delivers
     * the latched fault on that event (the second tier). Sequencing main <em>without</em> such an
     * event means racing the sampler, and the losing branch of that race is main halting while
     * spawned tasks are live — the leak the round-2 M2 change refuses at main's {@code FINISH_EVENT}
     * but cannot see on the {@code HaltTaskException} path, where no {@code FINISH_EVENT} for main
     * is emitted at all. A test built on that race would be a coin flip between the assertion below
     * and a hang, so it is not written; the observation is in the round-3 report instead.
     *
     * <p>{@code JmcAssume} <b>is</b> usable from a programmatic, uninstrumented test: it reports its
     * own {@code ASSUME_EVENT} by calling the runtime directly, exactly as
     * {@code org.mpi_sws.jmc.api.mp} does. That is shown by a mutation rather than by an assertion —
     * with the guard replaced by {@code m -> true} this program reaches {@code assume(false)} and
     * prunes all five iterations (round-3 report §3.1).
     *
     * <p>What makes this row more than a copy of
     * {@link #guardPerformingAnInstrumentedAccess_failsTheCheckLoudly} is {@code reachedAssume == 0}:
     * it shows the fault was delivered on main's join event and that main never got the chance to
     * prune the execution, which is the property the verdict was worried about.
     */
    @Test
    public void latchedFault_failsTheRunBeforeMainCanAssumeFalse() throws JmcCheckerException {
        for (long seed : FAULT_SEEDS) {
            final int[] reachedAssume = {0};
            final int[] ranPastAssume = {0};

            JmcCheckerException thrown =
                    assertThrows(
                            JmcCheckerException.class,
                            () ->
                                    run(
                                            "assumeFalseAfterALatchedFault",
                                            5,
                                            seed,
                                            () -> {
                                                MpProcess.Handle receiver =
                                                        MpProcess.spawn(
                                                                "receiver",
                                                                () ->
                                                                        Channel.recv(
                                                                                CommunicationModel
                                                                                        .ASYN,
                                                                                instrumentedReadGuard()));
                                                MpProcess.Handle sender =
                                                        MpProcess.spawn(
                                                                "sender",
                                                                () ->
                                                                        Channel.send(
                                                                                CommunicationModel
                                                                                        .ASYN,
                                                                                receiver.tid(),
                                                                                new Val(1)));
                                                sender.await();
                                                reachedAssume[0]++;
                                                JmcAssume.assume(false);
                                                ranPastAssume[0]++;
                                            }),
                            "seed " + seed + ": a latched fault must fail the run");

            String chain = messageChain(thrown);
            assertTrue(
                    chain.contains("instrumented access"),
                    "seed " + seed + ": the failure must name the re-entrant guard; chain: " + chain);
            assertEquals(
                    0,
                    reachedAssume[0],
                    "seed "
                            + seed
                            + ": the latched fault must be delivered on main's join event, before"
                            + " main can prune the execution with assume(false)");
            assertEquals(0, ranPastAssume[0], "seed " + seed + ": assume(false) never proceeds");
        }
    }

    // ------------------------------------------ M1 (round 3): the fault main swallowed

    /**
     * The three seeds the round-3 verdict measured the swallowed-fault shape on. On the round-3 code
     * the run <b>passed</b> — {@code total=5 blocked=0} — on the first two of them; the third failed
     * only by accident, because on its third iteration the guard happened to be evaluated on main's
     * own thread and the direct-throw path rethrew at reset.
     */
    private static final long[] SWALLOW_SEEDS = {42L, 1234567890123L, 20260910L};

    /**
     * The other half of round 3's M1: a fault main <b>catches</b> must still fail the run, at the end
     * of the iteration.
     *
     * <p>The program is the {@link #mainParkedOnReentrantGuard_failsTheRunNotTheIteration} shape —
     * main parks on a re-entrant guard, a spawned sender delayed by four {@code Nondet.chooseBoolean()}
     * calls re-resolves it and the guard faults on the sender's thread — with one change: main wraps
     * its receive in {@code catch (RuntimeException)}. That is precisely what {@code Channel}'s
     * Javadoc now forbids ("what a receive throws is JMC control flow"), and precisely what a user
     * who wraps a protocol step in a broad catch writes by accident, so it must be <em>reported</em>
     * rather than obeyed.
     *
     * <p>Until round 3 the latch was cleared on delivery: the {@code MpFaultValue} handed to the
     * parked main (or the direct throw on main's own thread) consumed it, main caught the exception,
     * the checker never recorded an error, and {@code check} returned a green {@code total=5
     * blocked=0} run over a program in which a forbidden guard had been detected five times. The
     * latch is now released only when the checker has <em>recorded</em> the error, so
     * {@code resetIteration} finds it still set and throws — the last resort of the class comment.
     *
     * <p>Both halves of that are asserted, and neither is implied by the other:
     * <ul>
     *   <li>{@code assertThrows} + "instrumented access" — the run fails, with the fault's own
     *       message, on <em>every</em> one of the three seeds (the round-3 code failed on one, for an
     *       unrelated reason);</li>
     *   <li>{@code caught[0] >= 1} — main really did swallow the delivered exception at least once
     *       before the run failed. Without it the row would also pass if the fault had simply been
     *       thrown somewhere main's {@code catch} never saw, i.e. if it were a copy of the
     *       {@code mainParkedOnReentrantGuard} row.</li>
     * </ul>
     * The count cannot be pinned exactly: the run ends at the first iteration whose reset reports the
     * fault, and which delivery tier that iteration used (the {@code MpFaultValue} to a parked main,
     * or the direct throw when the message was already pending) depends on the sampled schedule.
     * Both tiers reach main's {@code catch}, which is why one assertion covers both.
     *
     * <p>{@code passedTheReceive == 0} rules out the one shape that would make the whole row vacuous:
     * a receive that returned a message and then faulted for some other reason.
     */
    @Test
    public void mainCatchingTheDeliveredFault_isStillFailedAtTheEndOfTheIteration() {
        for (long seed : SWALLOW_SEEDS) {
            final int[] caught = {0};
            final int[] passedTheReceive = {0};

            JmcCheckerException thrown =
                    assertThrows(
                            JmcCheckerException.class,
                            () ->
                                    run(
                                            "mainSwallowingTheDeliveredFault",
                                            5,
                                            seed,
                                            () -> {
                                                MpProcess.Handle sender =
                                                        MpProcess.spawn(
                                                                "sender",
                                                                () -> {
                                                                    for (int i = 0; i < 4; i++) {
                                                                        Nondet.chooseBoolean();
                                                                    }
                                                                    Channel.send(
                                                                            CommunicationModel.ASYN,
                                                                            Tid.of(1L),
                                                                            new Val(1));
                                                                });
                                                try {
                                                    Channel.recv(
                                                            CommunicationModel.ASYN,
                                                            instrumentedReadGuard());
                                                    passedTheReceive[0]++;
                                                } catch (RuntimeException e) {
                                                    // Exactly the mistake Channel's Javadoc forbids.
                                                    caught[0]++;
                                                }
                                                sender.await();
                                            }),
                            "seed "
                                    + seed
                                    + ": a guard fault main caught must still fail the run at the"
                                    + " end of the iteration");

            String chain = messageChain(thrown);
            assertTrue(
                    chain.contains("instrumented access"),
                    "seed " + seed + ": the failure must name the re-entrant guard; chain: " + chain);
            assertTrue(
                    caught[0] >= 1,
                    "seed "
                            + seed
                            + ": main must have swallowed the delivered fault at least once before"
                            + " the run failed; caught = "
                            + caught[0]);
            assertEquals(
                    0, passedTheReceive[0], "seed " + seed + ": main's receive must not have returned");
        }
    }

    // ------------------------------------------- M2 (round 2): every spawned process is awaited

    /** The two seeds the M2 refusal rows are run under; the refusal must not depend on sampling. */
    private static final long[] ABANDON_SEEDS = {20260910L, 1234567890123L};

    /**
     * The fire-and-forget server — a first-class Must shape, and a hang under JMC: main spawns a
     * server looping on {@code recv}, sends it two requests and <b>returns without awaiting it</b>.
     *
     * <p>JMC ends the iteration at main's return and does not wait for spawned tasks, so the server
     * is not blocked but abandoned: {@code TaskManager.reset()} wakes it with {@code null} and its
     * late {@code FINISH_EVENT} and stray {@code enable()} land in the next iteration. The round-2
     * verdict measured this exact program six times: three hangs, two
     * {@code JmcCheckerException: No current task}, and once a <em>false</em> "guard performed an
     * instrumented access" fault raised against a guard that reads nothing. The round-2 change makes
     * the strategy refuse it at main's {@code FINISH_EVENT}, which is the only point at which the
     * strategy can still see both facts (main is ending; a process it started has not finished).
     *
     * <p>Discriminating: the message must contain "must be awaited" <em>and</em> the abandoned
     * process's task id in the list the strategy prints, so a hang, a {@code No current task} or the
     * false guard fault all fail this assertion rather than counting as the refusal.
     * {@link #mainAwaitingTheServerAfterShutdownMessage_runsGreen} is the same server with a
     * shutdown protocol, which must run green — so the refusal is shown to be about the missing
     * {@code await()} and not about servers.
     */
    @Test
    public void mainReturningWithServerStillParked_failsLoudly() {
        for (long seed : ABANDON_SEEDS) {
            final long[] serverRaw = {-1L};

            JmcCheckerException thrown =
                    assertThrows(
                            JmcCheckerException.class,
                            () ->
                                    run(
                                            "fireAndForgetServer",
                                            ITERATIONS,
                                            seed,
                                            () -> {
                                                MpProcess.Handle server =
                                                        MpProcess.spawn(
                                                                "server",
                                                                () -> {
                                                                    while (true) {
                                                                        Channel.recv(
                                                                                CommunicationModel
                                                                                        .ASYN);
                                                                    }
                                                                });
                                                serverRaw[0] = server.tid().raw();
                                                Channel.send(
                                                        CommunicationModel.ASYN,
                                                        server.tid(),
                                                        new Val(1));
                                                Channel.send(
                                                        CommunicationModel.ASYN,
                                                        server.tid(),
                                                        new Val(2));
                                                // No server.await(): the shape under test.
                                            }),
                            "seed " + seed + ": main must not be allowed to abandon a live process");

            String chain = messageChain(thrown);
            assertTrue(
                    chain.contains("must be awaited"),
                    "seed " + seed + ": the failure must state the rule; chain: " + chain);
            assertTrue(
                    chain.contains("[" + serverRaw[0] + "]"),
                    "seed "
                            + seed
                            + ": the failure must name the abandoned process ("
                            + serverRaw[0]
                            + "); chain: "
                            + chain);
        }
    }

    /**
     * The same server with a shutdown protocol, awaited: the control that shows the refusal is about
     * the missing {@code await()} and not about the program shape.
     *
     * <p>Main sends two requests and then a {@code Val(0)}, on which the server's loop exits; main
     * then awaits it. Under {@code asyn} the server may read the shutdown message before either
     * request (Def. 3.4 admits any pending matching message), so the number of requests it served is
     * not fixed — but it terminates in every iteration, and that is what is asserted.
     *
     * <p>Three assertions, all of which the abandoned shape fails: every iteration ran to the full
     * budget; the server's loop exited on the shutdown message in every iteration; and
     * {@code blockedIterations} is zero, so the server's receives were parked-and-answered rather
     * than blocked — the distinction {@link #noIterationBlocked_reportsZeroBlockedIterations} pins
     * in the simplest case, here on the shape M2 is about.
     */
    @Test
    public void mainAwaitingTheServerAfterShutdownMessage_runsGreen() throws JmcCheckerException {
        final int[] entered = {0};
        final int[] shutdowns = {0};
        final int[] served = {0};
        final int[] mainRanOn = {0};

        JmcModelCheckerReport report =
                run(
                        "serverWithAShutdownMessage",
                        ITERATIONS,
                        () -> {
                            entered[0]++;
                            MpProcess.Handle server =
                                    MpProcess.spawn(
                                            "server",
                                            () -> {
                                                while (true) {
                                                    Val v =
                                                            Channel.recv(
                                                                    CommunicationModel.ASYN,
                                                                    Val.class);
                                                    if (v.v() == 0) {
                                                        break;
                                                    }
                                                    served[0]++;
                                                }
                                                shutdowns[0]++;
                                            });
                            Channel.send(CommunicationModel.ASYN, server.tid(), new Val(1));
                            Channel.send(CommunicationModel.ASYN, server.tid(), new Val(2));
                            Channel.send(CommunicationModel.ASYN, server.tid(), new Val(0));
                            server.await();
                            mainRanOn[0]++;
                        });

        assertEquals(ITERATIONS, entered[0], "the program body must run once per iteration");
        assertEquals(ITERATIONS, report.getTotalIterations(), "every iteration ran");
        assertEquals(
                0,
                report.getBlockedIterations(),
                "every receive of this program is answered, so nothing may be blocked");
        assertEquals(
                ITERATIONS, shutdowns[0], "the server must exit on the shutdown message every time");
        assertEquals(ITERATIONS, mainRanOn[0], "main must run on after awaiting the server");
        assertTrue(
                served[0] <= 2 * ITERATIONS,
                "the server cannot serve more requests than were sent; served " + served[0]);
    }

    /**
     * The rule is "<b>live</b>", not "parked": a child that is merely paused mid-body when main
     * returns is abandoned exactly as a parked one is, and must be refused the same way.
     *
     * <p>The child runs sixty {@code Nondet.chooseBoolean()} calls and never receives anything, so
     * the strategy's {@code parked} map is empty at main's {@code FINISH_EVENT} and only the
     * started-and-not-finished bookkeeping can see it. Main takes three of its own nondet steps and
     * returns; {@code JmcThread.start()} pauses the spawning task until the child has reported
     * {@code START_EVENT}, so the child is in the live set from the moment {@code spawn} returns
     * whatever the sampler does next, and it would need to win sixty consecutive draws against
     * main's three to have finished.
     *
     * <p>Discriminating: the message must <em>not</em> mention parked receives — the strategy appends
     * "(parked: …)" only when {@code parked} is non-empty — so a refusal that fired for the parked
     * reason rather than the live one fails this assertion. That is the whole point of the row.
     */
    @Test
    public void mainReturningWithChildStillRunning_failsLoudly() {
        for (long seed : ABANDON_SEEDS) {
            final long[] childRaw = {-1L};
            final int[] childSteps = {0};

            JmcCheckerException thrown =
                    assertThrows(
                            JmcCheckerException.class,
                            () ->
                                    run(
                                            "childStillRunningAtMainsReturn",
                                            ITERATIONS,
                                            seed,
                                            () -> {
                                                MpProcess.Handle child =
                                                        MpProcess.spawn(
                                                                "child",
                                                                () -> {
                                                                    for (int i = 0; i < 60; i++) {
                                                                        Nondet.chooseBoolean();
                                                                        childSteps[0]++;
                                                                    }
                                                                });
                                                childRaw[0] = child.tid().raw();
                                                for (int i = 0; i < 3; i++) {
                                                    Nondet.chooseBoolean();
                                                }
                                                // No child.await(): the shape under test.
                                            }),
                            "seed " + seed + ": a live child at main's return must fail the run");

            String chain = messageChain(thrown);
            assertTrue(
                    chain.contains("must be awaited"),
                    "seed " + seed + ": the failure must state the rule; chain: " + chain);
            assertTrue(
                    chain.contains("[" + childRaw[0] + "]"),
                    "seed "
                            + seed
                            + ": the failure must name the abandoned child ("
                            + childRaw[0]
                            + "); chain: "
                            + chain);
            assertFalse(
                    chain.contains("parked:"),
                    "seed "
                            + seed
                            + ": this child is running, not parked — the rule under test is"
                            + " liveness; chain: "
                            + chain);
            assertTrue(
                    childSteps[0] < 60,
                    "seed " + seed + ": the child must still have been mid-body; steps "
                            + childSteps[0]);
        }
    }

    // ------------------------------------------------------- m1: the replay-placeholder refusal

    /**
     * {@code "mp-random"} with the option of answering one receive with a delivery whose value is
     * the adapter's {@code ReplayPlaceholder} — the only way to reach the refusal
     * {@code Channel.request} carries, since nothing in a route-A program can produce that value
     * (the placeholder is minted by {@code MpDeliveryValueAdapter.fromJson}, and replay is a TODO).
     *
     * <p>{@code nextTask} is the hook rather than {@code updateEvent} because the strategy's
     * {@code answers} map is private: the answer becomes reachable only once it has been attached to
     * a {@link SchedulingChoice}, which is what {@code super.nextTask()} returns. The substitution
     * keeps the send's identity ({@code src}, {@code sendIndex}) and replaces only the message, which
     * is exactly the shape a replaying strategy would hand over if it forgot to resolve the send
     * from its own buffers.
     *
     * <p>Annotated {@link JmcIgnoreInstrumentation} for the reason {@code MpApiWiringSnapshotTest}'s
     * {@code Snapshotting} is: a strategy in an instrumented package runs on the task thread from
     * inside the runtime, where an instrumented field access would report an event from within event
     * processing. The annotation is read per class file and so applies to this nested class alone.
     */
    @JmcIgnoreInstrumentation
    private static final class PlaceholderInjecting extends MpRandomSchedulingStrategy {

        /** Whether to substitute the placeholder; {@code false} makes this the control. */
        private final boolean inject;

        /** Non-{@code ⊥} deliveries seen, whether or not substituted. */
        private int deliveries;

        /** Deliveries whose message was replaced by the placeholder. */
        private int substitutions;

        PlaceholderInjecting(boolean inject) {
            super(SEED, REPORT_PATH);
            this.inject = inject;
        }

        @Override
        public SchedulingChoice<?> nextTask() {
            SchedulingChoice<?> choice = super.nextTask();
            if (choice == null
                    || !(choice.getValue() instanceof MpDeliveryValue d)
                    || d.isBottom()) {
                return choice;
            }
            deliveries++;
            if (!inject) {
                return choice;
            }
            substitutions++;
            return SchedulingChoice.task(
                    choice.getTaskId(),
                    MpDeliveryValue.of(
                            d.src(),
                            d.sendIndex(),
                            MpDeliveryValueAdapter.ReplayPlaceholder.INSTANCE));
        }
    }

    /**
     * The m5 refusal, which until now was on the path of every receive and tested in neither
     * direction — and whose untested edit is what finding F-F was: a delivery whose value is the
     * adapter's {@code ReplayPlaceholder} must be refused at the unwrap, naming replay.
     *
     * <p>The program is one self-send and one receive, so there is exactly one delivery to
     * substitute and no ambiguity about which answer the refusal fired on. The assertions are the
     * message ("replay"), the substitution count (so the injection is shown to have happened rather
     * than the run having failed for some other reason), and that the receive never returned — a
     * refusal reported <em>after</em> handing the placeholder to the program would be worthless.
     *
     * <p>{@link #unmodifiedDelivery_reachesTheTask_theControlForThePlaceholderRefusal} is the same
     * strategy with {@code inject == false}: same hook, same delivery, refusal silent, message
     * delivered. Without that row this test could not distinguish "the placeholder is refused" from
     * "any strategy that overrides {@code nextTask} breaks the run".
     */
    @Test
    public void replayPlaceholderDelivery_isRefusedAtTheUnwrap() throws JmcCheckerException {
        final int[] passedTheReceive = {0};
        PlaceholderInjecting strategy = new PlaceholderInjecting(true);
        JmcModelChecker checker = checkerWith(strategy, 1);

        JmcCheckerException thrown =
                assertThrows(
                        JmcCheckerException.class,
                        () ->
                                checker.check(
                                        new JmcFunctionalTestTarget(
                                                "placeholderDelivery",
                                                () -> {
                                                    Channel.send(
                                                            CommunicationModel.ASYN,
                                                            MpProcess.self(),
                                                            new Val(7));
                                                    Channel.recv(
                                                            CommunicationModel.ASYN, Val.class);
                                                    passedTheReceive[0]++;
                                                })),
                        "a ReplayPlaceholder reaching a task must fail the run");

        String chain = messageChain(thrown);
        assertTrue(
                chain.contains("replay"),
                "the refusal must name replay as the unsupported feature; chain: " + chain);
        assertEquals(1, strategy.substitutions, "exactly one delivery was substituted");
        assertEquals(0, passedTheReceive[0], "the receive must not have delivered the placeholder");
    }

    /**
     * The control for {@link #replayPlaceholderDelivery_isRefusedAtTheUnwrap}: the same program under
     * the same {@code nextTask} override with the substitution switched off completes and delivers
     * the message that was sent.
     *
     * <p>{@code deliveries == 1} is asserted on both rows, so the two differ in exactly one thing —
     * whether the message was replaced — and the refusal is shown to be about the placeholder rather
     * than about the hook or about {@code Channel.request}'s unwrap firing on ordinary deliveries
     * (which is precisely the mistake F-F was).
     */
    @Test
    public void unmodifiedDelivery_reachesTheTask_theControlForThePlaceholderRefusal()
            throws JmcCheckerException {
        final int[] got = {-1};
        PlaceholderInjecting strategy = new PlaceholderInjecting(false);

        checkerWith(strategy, 1)
                .check(
                        new JmcFunctionalTestTarget(
                                "unmodifiedDelivery",
                                () -> {
                                    Channel.send(
                                            CommunicationModel.ASYN,
                                            MpProcess.self(),
                                            new Val(7));
                                    got[0] = Channel.recv(CommunicationModel.ASYN, Val.class).v();
                                }));

        assertEquals(7, got[0], "the unmodified delivery must reach the task");
        assertEquals(1, strategy.deliveries, "the same hook saw the same single delivery");
        assertEquals(0, strategy.substitutions, "the control substitutes nothing");
    }

    // -------------------------------------------------------------- n1: the count is per check

    /**
     * {@code blockedIterations} is per <em>check</em>, not per strategy instance: one instance driven
     * through two {@code check} calls must not carry the first run's count into the second report.
     *
     * <p>Unreachable through {@code strategyType("mp-random")}, which builds one instance per
     * {@code toRuntimeConfiguration()} — but reachable through {@code strategyConstructor}, which is
     * how every other instance-level claim in this class is made, so the invariant is worth pinning
     * rather than documenting. The fix resets the field at {@code initIteration(0, …)} rather than in
     * {@code teardown}, which keeps the accessor valid after {@code check} returns; both halves of
     * that are asserted, on the report and on the accessor.
     *
     * <p>The two programs are the extremes of the counter — every iteration blocked, and none — so a
     * count that carried over would show up as {@code 3} where {@code 0} is expected, and the third
     * assertion ({@code got == 5}) rules out a zero that comes from a second run in which nothing
     * happened at all.
     */
    @Test
    public void blockedIterations_resetsBetweenChecksOfTheSameInstance() throws JmcCheckerException {
        final int checks = 3;
        MpRandomSchedulingStrategy strategy = new MpRandomSchedulingStrategy(SEED, REPORT_PATH);

        final int[] firstEntered = {0};
        JmcModelCheckerReport first =
                checkerWith(strategy, checks)
                        .check(
                                new JmcFunctionalTestTarget(
                                        "alwaysBlocked",
                                        () -> {
                                            firstEntered[0]++;
                                            Channel.recv(CommunicationModel.ASYN);
                                        }));

        assertEquals(checks, firstEntered[0], "the first program must run once per iteration");
        assertEquals(checks, first.getBlockedIterations(), "every iteration of the first is blocked");
        assertEquals(
                checks,
                strategy.blockedIterations(),
                "the accessor must still be valid after check returns");

        final int[] secondEntered = {0};
        final int[] got = {-1};
        JmcModelCheckerReport second =
                checkerWith(strategy, checks)
                        .check(
                                new JmcFunctionalTestTarget(
                                        "neverBlocked",
                                        () -> {
                                            secondEntered[0]++;
                                            Channel.send(
                                                    CommunicationModel.ASYN,
                                                    MpProcess.self(),
                                                    new Val(5));
                                            got[0] =
                                                    Channel.recv(
                                                                    CommunicationModel.ASYN,
                                                                    Val.class)
                                                            .v();
                                        }));

        assertEquals(checks, secondEntered[0], "the second program must run once per iteration");
        assertEquals(checks, second.getTotalIterations(), "every iteration of the second ran");
        assertEquals(
                0,
                second.getBlockedIterations(),
                "the first check's count must not carry into the second report");
        assertEquals(
                0, strategy.blockedIterations(), "nor into the accessor of the reused instance");
        assertEquals(5, got[0], "the second program really delivered its message");
    }

    /**
     * Every message on a cause chain, concatenated — the same helper
     * {@code MpApiStrategySupportTest} uses, and for the same reason: the interesting text may be on
     * any link of the chain.
     *
     * @param thrown the exception the checker failed with
     * @return the concatenated messages
     */
    private static String messageChain(Throwable thrown) {
        StringBuilder out = new StringBuilder();
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            out.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append(" | ");
            if (t.getCause() == t) {
                break;
            }
        }
        return out.toString();
    }
}
