package org.mpi_sws.jmc.test.mp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.annotations.JmcExpectExecutions;
import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.test.mp.MpTestMessages.Ping;
import org.mpi_sws.jmc.test.mp.MpTestMessages.Pong;
import org.mpi_sws.jmc.test.mp.MpTestMessages.Val;

import java.util.List;
import java.util.Optional;

/**
 * Route A end to end: programs written against {@code org.mpi_sws.jmc.api.mp} running under
 * {@code JmcRuntime} and the bytecode agent, answered by {@code "mp-random"}
 * ({@code org.mpi_sws.jmc.strategies.mp.MpRandomSchedulingStrategy}).
 *
 * <p>Contract sections pinned: plan/state/mp-contract.md §2 (the event vocabulary —
 * {@code MP_SEND_EVENT}, {@code MP_RECV_REQUEST_EVENT}, {@code ND_EVENT}), §3 (who answers and what
 * comes back), §4 (the frozen signatures, including amendment A2: {@code Tid}/{@code MpMessage}/
 * {@code CommunicationModel} live in {@code mp.events} and {@code MpProcess.self()} replaces
 * {@code Tid.current()}), and §6.2. The delivery rules exercised are those of plan/state/state.md
 * §5.2; the causal-order rows are rose-state.md F7(b) (spawn is a {@code porf} edge) and S2 review
 * round 2 major M3 (the clock ticks on emit, so one sender's {@code cd} sends are ordered).
 *
 * <p><b>Law L1 is what these tests are really about.</b> Every branch point below —
 * which of two pending sends a receive reads, whether a non-blocking receive reads {@code ⊥}, what
 * {@code nondet} returns — is asserted to be resolved by the <em>strategy</em>. Two of them are
 * asserted by observing that both outcomes occur across iterations, which is only possible if the
 * choice reaches a sampler; a library that decided internally would produce one outcome forever.
 *
 * <h2>Two facts about JMC that shape every program below</h2>
 *
 * <p><b>(a) A failure in a spawned task cannot fail the test.</b> {@code JmcThread.run} catches
 * everything {@code run1()} throws and only {@code LOGGER.error}s it (rose-state.md §S0, "a third
 * defect") — and an {@code AssertionError}, being an {@code Error} and not an {@code Exception},
 * does not even reach that catch: it escapes to the uncaught-exception handler, which reports a
 * {@code HALT_EVENT} for a task whose {@code finally} has already reported {@code FINISH_EVENT}
 * (S3 review round 1, n1). An {@code assertEquals} inside a spawned body is therefore invisible to
 * the checker whichever way it fails. So every spawned process here <em>records</em> into an array
 * captured from the enclosing method and the <em>main</em> task asserts, after awaiting it. An
 * assertion in the main task propagates out of {@code JmcModelChecker.check} and fails the test,
 * which is the behaviour we need.
 *
 * <p><b>(b) Cross-iteration recording uses an instance field, not a static.</b>
 * {@code JmcMethodTestDescriptor.execute} constructs the test class <em>once</em> and
 * {@code JmcTestExecutor.execute} reuses that one instance for every iteration, so an instance
 * field accumulates exactly across the iterations of one method and is fresh for the next method —
 * no static field, and therefore none of the cross-method contamination of rose-state.md §S0.1.
 * The fields are {@code final} arrays because {@code JmcReadWriteVisitor} skips final fields, so
 * reading them emits no event and adds no scheduling point; array element access is never
 * instrumented.
 *
 * <p><b>Residual risk of the probabilistic assertions.</b> Six phases below assert that
 * <em>both</em> (or all three) outcomes of a choice are observed across 60 iterations, and check
 * that on the last iteration. They do not all rest on the same argument (S3 review round 1, m8):
 *
 * <ul>
 *   <li>For {@code recv_receivesValueChosenByTheStrategy…}, {@code tryRecv_withMatchingSendPending…}
 *       and {@code nondet_isAnsweredByTheStrategy} the draw is a single call on the seeded
 *       generator over a candidate set that is the same in every iteration — both senders are
 *       awaited before the receive, or there is exactly one request, or exactly one choice. Each
 *       iteration is then an independent uniform draw and missing an outcome has probability at
 *       most {@code 3 * (2/3)^60 < 10^-10}.</li>
 *   <li>For the two {@code cd}/{@code asyn} <em>control</em> phases the bound is weaker. Whether
 *       both messages are pending when the receiver's first request is resolved depends on the
 *       schedule, and an iteration in which only one is pending is forced rather than drawn — so
 *       the per-iteration probability of the reversed order is neither uniform nor independent of
 *       the schedule. The true miss probability is larger, of the order of
 *       {@code (3/4)^60 ≈ 3 * 10^-8}, still negligible.</li>
 * </ul>
 *
 * <p>In both cases the risk is in the safe direction: an unlucky run <em>fails</em> rather than
 * passing silently. Forcing a particular strategy choice deterministically would mean controlling
 * the sampler, i.e. the mechanism under test, which is why the probabilistic form is accepted here
 * exactly as {@code ObjectNotifyAllTest} accepts it for {@code notifyAll}.
 *
 * <p><b>Iteration accounting.</b> {@code numIterations} and {@code @JmcExpectExecutions} agree:
 * {@code JmcModelChecker.check} counts an iteration when it runs and reports that count (branch
 * {@code fix/checker-iteration-accounting}, cherry-picked here). Before that fix the report was one
 * short of the number of bodies run, which is why an earlier revision of this file set
 * {@code numIterations} one higher than the expectation.
 *
 * <p><b>{@code @JmcExpectExecutions} now sees a blocked iteration.</b> The annotation is checked
 * against {@code total − blocked}, and since the S3 review (M1) {@code mp-random} counts an
 * iteration as blocked the first time it issues {@code SchedulingChoice.blockTask}. So the
 * expectations below really are the claim "every iteration ran <em>and</em> nothing was blocked":
 * a run in which a receive found no sender and was blocked reports fewer completed iterations and
 * fails. Before M1 nothing outside {@code strategies/trust/Algo} ever called
 * {@code setBlockedIterations}, a fully blocked run reported "all iterations completed", and the
 * discriminating power credited to these rows was unearned (M4). The blocked/not-blocked
 * distinction itself is pinned by {@code MpBlockingAndGuardTest}.
 *
 * <p><b>A blocked <em>main</em> on this path.</b> The eight rows above are all "nothing blocks";
 * the last two are the other side, and they are here rather than in {@code MpBlockingAndGuardTest}
 * because the claim is about the {@code @JmcCheck} route itself (S3 review round 3, M2).
 * {@code JmcTestExecutor} invokes the body reflectively and used to wrap every
 * {@code InvocationTargetException} cause in a {@code RuntimeException}, so main's
 * {@code HaltTaskException} — a blocked receive, or {@code JmcAssume.assume(false)} — landed in
 * {@code JmcModelChecker.check}'s catch-all and failed the run with an opaque
 * {@code InvocationTargetException} instead of ending the iteration. Commit {@code cf6785c3}
 * rethrows such a cause unwrapped. Everything else in this class runs on that same path, which is
 * why all eight rows above are re-run whenever it changes.
 */
public class MpApiWiringTest {

    /**
     * The number of times each probabilistic method's body runs, which is also the reported
     * execution count (see the class comment).
     */
    private static final int ITERATIONS = 60;

    /**
     * One iteration counter per probabilistic method, so no two of them share a slot; see fact (b)
     * in the class comment.
     */
    private final int[] runs = new int[5];

    /** Which values {@code recv} returned across the iterations of the {@code recv} method. */
    private final boolean[] recvSawValue = new boolean[3];

    /** Whether {@code tryRecv} was seen empty (index 0) and non-empty (index 1). */
    private final boolean[] tryRecvSawOutcome = new boolean[2];

    /** Which of the three {@code nondet} options was returned. */
    private final boolean[] nondetSawOption = new boolean[3];

    /** Whether the concurrent {@code cd} control pair arrived 3-then-4 (0) or 4-then-3 (1). */
    private final boolean[] cdControlSawOrder = new boolean[2];

    /** Whether the {@code asyn} control pair arrived 3-then-4 (0) or 4-then-3 (1). */
    private final boolean[] asynControlSawOrder = new boolean[2];

    /** The iteration budget of the two blocked-main methods; see their Javadoc. */
    private static final int BLOCKED_ITERATIONS = 10;

    /** How many times each of the two blocked-main methods' bodies has run. */
    private final int[] blockedRuns = new int[2];

    /** How many times each of those methods' silent child ran to completion. */
    private final int[] silentChildRuns = new int[2];

    // ------------------------------------------------------------------ the wiring, end to end

    /**
     * The smallest complete round trip: main spawns a ponger, sends it a {@code Ping} carrying its
     * own {@link MpProcess#self()}, the ponger receives and replies, main receives the reply.
     * Exercises {@code MP_SEND_EVENT}, {@code MP_RECV_REQUEST_EVENT}, the strategy's park/retry path
     * (whichever of the two receives runs first has nothing to read and must be parked, not
     * spun on), and the delivery of the answer through {@code SchedulingChoice}'s value.
     *
     * <p>Non-vacuous in three ways: the echoed value is asserted to be {@code 41 + 1}, so a
     * {@code recv} that returned the request instead of the reply, or a stale message, fails;
     * {@code @JmcExpectExecutions} pins that every iteration completed, so a run that blocked or
     * halted early fails — the annotation compares against {@code total − blocked}, and since the
     * S3 review (M1) {@code mp-random} counts a blocked iteration, so an iteration in which either
     * receive found no sender is subtracted here and this row goes red; and the ponger's receipt is
     * recorded and asserted, so a ponger whose body never ran (fact (a)) fails rather than passing
     * quietly.
     */
    @JmcCheck
    @JmcCheckConfiguration(strategy = "mp-random", numIterations = 10)
    @JmcExpectExecutions(10)
    // Checkstyle's MethodName suppression is an XPath filter keyed on a method annotation whose
    // IDENT contains "Test"; @JmcCheck does not, so a @JmcCheck test needs the suppression a
    // @Test one does not. Measured: removing the eight annotations in this class raises
    // :integration-test:checkstyleTest from 777 to 785 warnings, exactly these eight names.
    @SuppressWarnings("checkstyle:MethodName")
    public void pingPong_underMpRandom_completesAllIterations() {
        final Tid main = MpProcess.self();
        final int[] pongerSaw = {-1};

        MpProcess.Handle ponger =
                MpProcess.spawn(
                        "ponger",
                        () -> {
                            Ping p = Channel.recv(CommunicationModel.ASYN, Ping.class);
                            pongerSaw[0] = p.v();
                            Channel.send(CommunicationModel.ASYN, p.from(), new Pong(p.v() + 1));
                        });

        Channel.send(CommunicationModel.ASYN, ponger.tid(), new Ping(main, 41));
        Pong reply = Channel.recv(CommunicationModel.ASYN, Pong.class);
        ponger.await();

        assertEquals(41, pongerSaw[0], "the ponger must have received the ping");
        assertEquals(42, reply.v(), "main must read the reply, not the request");
    }

    /**
     * Two sends from one sender on one {@code p2p} channel are received in send order.
     *
     * <p>This is a <em>tautology</em> under {@code p2p} — Def. 3.5's {@code so ≜ [S^p2p]; po;
     * [S^p2p]} says exactly this, and {@code Delivery.deliverable} offers only the earliest matching
     * pending message per sender, so the strategy has no second option to pick. It earns its place
     * as a smoke test of the wiring: it fails if {@code sendIndex} is not assigned in program order,
     * if {@code Buffers.pending(P2P, dst)} does not return a sender's queue in emission order, or if
     * the answer for the second receive is delivered to the wrong task.
     *
     * <p>The receiver records rather than asserts (fact (a)); main asserts after the join. The
     * discriminating half is the third assertion: the receiver is asserted to have run to
     * completion, so a receiver body that was silently killed cannot leave {@code {1, 2}} behind.
     */
    @JmcCheck
    @JmcCheckConfiguration(strategy = "mp-random", numIterations = 10)
    @JmcExpectExecutions(10)
    @SuppressWarnings("checkstyle:MethodName")
    public void p2p_twoSendsFromOneSender_arriveInOrder() {
        final int[] got = {-1, -1};
        final boolean[] finished = {false};

        MpProcess.Handle receiver =
                MpProcess.spawn(
                        "receiver",
                        () -> {
                            got[0] = Channel.recv(CommunicationModel.P2P, Val.class).v();
                            got[1] = Channel.recv(CommunicationModel.P2P, Val.class).v();
                            finished[0] = true;
                        });

        Channel.send(CommunicationModel.P2P, receiver.tid(), new Val(1));
        Channel.send(CommunicationModel.P2P, receiver.tid(), new Val(2));
        receiver.await();

        assertTrue(finished[0], "the receiver must have completed both receives");
        assertEquals(1, got[0], "p2p delivers a sender's first send first");
        assertEquals(2, got[1], "p2p delivers a sender's second send second");
    }

    /**
     * The choice of which pending send a receive reads is the strategy's, not the library's (L1).
     *
     * <p>Two distinct {@code asyn} messages are made pending before main receives — each from its
     * own process, joined first, so both are certainly in the buffers when the request is emitted.
     * Under Def. 3.4 both are deliverable, so {@code Delivery.deliverable} returns a two-element set
     * and {@code MpRandomSchedulingStrategy} samples it. Across {@code ITERATIONS} iterations both
     * values must be observed: a {@code Channel.recv} that picked a candidate itself — the F1 defect
     * this API was rewritten to make impossible — would return the same one every time and fail the
     * final assertion.
     *
     * <p>Non-vacuous in both directions: every iteration asserts the value is one of exactly the two
     * that were sent (so "the recorder saw both" cannot be satisfied by garbage), and the last
     * iteration asserts both were seen (so "the choice is real" cannot be satisfied by a constant).
     * Probabilistic; see the class comment for the residual risk.
     */
    @JmcCheck
    @JmcCheckConfiguration(strategy = "mp-random", numIterations = ITERATIONS)
    @JmcExpectExecutions(ITERATIONS)
    @SuppressWarnings("checkstyle:MethodName")
    public void recv_receivesValueChosenByTheStrategy_notByTheLibrary() {
        runs[0]++;
        final Tid main = MpProcess.self();

        MpProcess.Handle first =
                MpProcess.spawn("s1", () -> Channel.send(CommunicationModel.ASYN, main, new Val(1)));
        MpProcess.Handle second =
                MpProcess.spawn("s2", () -> Channel.send(CommunicationModel.ASYN, main, new Val(2)));
        first.await();
        second.await();

        int v = Channel.recv(CommunicationModel.ASYN, Val.class).v();
        assertTrue(v == 1 || v == 2, "recv returned a value nobody sent: " + v);
        recvSawValue[v] = true;

        if (runs[0] == ITERATIONS) {
            assertTrue(recvSawValue[1], "the strategy never chose the send from s1");
            assertTrue(recvSawValue[2], "the strategy never chose the send from s2");
        }
    }

    /**
     * A non-blocking receive with nothing it can read returns {@code ⊥} — {@link Optional#empty()}.
     *
     * <p>Deterministic: with no candidate, the strategy's option set is {@code {⊥}} alone, so this
     * holds in every iteration. Two halves make it non-vacuous. The second {@code tryRecv} runs
     * <em>after</em> a message has been made pending but carries a guard that excludes it, showing
     * that "empty" means "nothing this receive may read" and not "the buffers are empty" — the
     * {@code ∩ mval} restriction of Def. 3.4. The closing blocking {@code recv} then reads that same
     * message, proving it really was pending throughout, so neither empty result can be explained by
     * a send that never happened.
     */
    @JmcCheck
    @JmcCheckConfiguration(strategy = "mp-random", numIterations = 10)
    @JmcExpectExecutions(10)
    @SuppressWarnings("checkstyle:MethodName")
    public void tryRecv_withNothingPending_returnsEmpty() {
        final Tid main = MpProcess.self();

        assertTrue(
                Channel.tryRecv(CommunicationModel.ASYN).isEmpty(),
                "a non-blocking receive with an empty buffer must read ⊥");

        Channel.send(CommunicationModel.ASYN, main, new Val(7));

        assertTrue(
                Channel.tryRecv(CommunicationModel.ASYN, Val.class, m -> m.v() == 8).isEmpty(),
                "a guard that matches nothing pending must read ⊥");
        assertEquals(
                7,
                Channel.recv(CommunicationModel.ASYN, Val.class).v(),
                "the message excluded by the guard must still be pending");
    }

    /**
     * With a matching send pending, a non-blocking receive may still read {@code ⊥} — Must §2.3.2:
     * {@code ⊥} is offered as one more reads-from option <em>in addition to</em> every consistent
     * send, whether or not anything is pending. So both outcomes must occur across iterations.
     *
     * <p>This is the assertion that separates a correct non-blocking receive from the natural wrong
     * one ("return the message if there is one, else empty"), which would lose every behaviour in
     * which a timeout fires despite an available message — review round 1 blocker B1 of
     * plan/state/state.md, on route A. Every iteration also asserts that a non-empty result carries
     * exactly the value sent, so the recorder cannot be satisfied by a spurious message.
     * Probabilistic; see the class comment.
     */
    @JmcCheck
    @JmcCheckConfiguration(strategy = "mp-random", numIterations = ITERATIONS)
    @JmcExpectExecutions(ITERATIONS)
    @SuppressWarnings("checkstyle:MethodName")
    public void tryRecv_withMatchingSendPending_bothOutcomesObservedAcrossIterations() {
        runs[1]++;
        final Tid main = MpProcess.self();

        Channel.send(CommunicationModel.ASYN, main, new Val(5));
        Optional<Val> got = Channel.tryRecv(CommunicationModel.ASYN, Val.class);

        if (got.isPresent()) {
            assertEquals(5, got.get().v(), "a delivered message must be the one that was sent");
            tryRecvSawOutcome[1] = true;
        } else {
            tryRecvSawOutcome[0] = true;
        }

        if (runs[1] == ITERATIONS) {
            assertTrue(
                    tryRecvSawOutcome[1],
                    "the strategy never delivered the pending message to a non-blocking receive");
            assertTrue(
                    tryRecvSawOutcome[0],
                    "the strategy never chose ⊥ although a matching message was pending"
                            + " (Must §2.3.2)");
        }
    }

    /**
     * {@code nondet(S)} is answered by the strategy through {@code ND_EVENT}, never by a random
     * number generator inside {@code Nondet} (plan/state/mp-contract.md §2.3, D5).
     *
     * <p>All three options must be observed across the iterations. A {@code Nondet} that decided
     * internally would still vary if it used its own generator — which is why this is paired with
     * {@code MpApiStrategySupportTest#ndEvent_neverFallsBackToJavaUtilRandom}, whose point is that
     * under a strategy that does <em>not</em> answer {@code ND_EVENT} the call throws instead of
     * quietly returning a value. Together they pin: answered by the strategy, or not at all.
     *
     * <p>Non-vacuous per iteration: the returned option is asserted to be one of the three offered,
     * in the caller's list order, so index and value cannot have drifted apart. Probabilistic; see
     * the class comment.
     */
    @JmcCheck
    @JmcCheckConfiguration(strategy = "mp-random", numIterations = ITERATIONS)
    @JmcExpectExecutions(ITERATIONS)
    @SuppressWarnings("checkstyle:MethodName")
    public void nondet_isAnsweredByTheStrategy() {
        runs[2]++;

        List<Integer> options = List.of(10, 20, 30);
        int chosen = Nondet.choose(options);

        assertTrue(options.contains(chosen), "nondet returned a value not in S: " + chosen);
        nondetSawOption[options.indexOf(chosen)] = true;

        if (runs[2] == ITERATIONS) {
            assertTrue(nondetSawOption[0], "nondet never returned min(S)");
            assertTrue(nondetSawOption[1], "nondet never returned the second option");
            assertTrue(nondetSawOption[2], "nondet never returned the last option");
        }
    }

    /**
     * Spawn is a {@code porf} edge, so a child's {@code cd} send is causally after everything its
     * parent did before the spawn — rose-state.md F7(b), on route A.
     *
     * <p>Phase 1 (the claim, every iteration): main sends {@code m1} under {@code cd} to R, then
     * spawns a child that sends {@code m2} under {@code cd} to the same R. R receives twice and must
     * see {@code m1} first. The mechanism is the clock algebra of plan/state/state.md §5.2:
     * {@code m1}'s stamp is main's clock after the tick, the child inherits main's clock at
     * {@code TaskClocks.spawn}, and {@code m2}'s stamp strictly dominates {@code m1}'s — so Def.
     * 3.6 leaves {@code m2} out of the deliverable set while {@code m1} is pending. Drop the
     * inheritance and the two sends become {@code cd}-concurrent and either order is admitted, which
     * is the defect this row guards.
     *
     * <p>Phase 2 (the discriminating construction, checked once at the end): the same two-message
     * shape with the two senders genuinely concurrent — two children spawned from main with no
     * {@code porf} path between them. Their stamps are incomparable, Def. 3.6 admits both, and both
     * orders must be observed across the iterations. Without it, phase 1 would also pass under a
     * scheduler that simply never reorders anything; with it, the strategy is shown to reorder
     * freely exactly when causality permits. Probabilistic; see the class comment.
     */
    @JmcCheck
    @JmcCheckConfiguration(strategy = "mp-random", numIterations = ITERATIONS)
    @JmcExpectExecutions(ITERATIONS)
    @SuppressWarnings("checkstyle:MethodName")
    public void cd_spawnedChildsSendIsOrderedAfterParentsPriorSend() {
        runs[3]++;

        // Phase 1: causally ordered pair.
        final int[] causal = {-1, -1};
        final boolean[] causalDone = {false};
        MpProcess.Handle receiver =
                MpProcess.spawn(
                        "R",
                        () -> {
                            causal[0] = Channel.recv(CommunicationModel.CD, Val.class).v();
                            causal[1] = Channel.recv(CommunicationModel.CD, Val.class).v();
                            causalDone[0] = true;
                        });
        Channel.send(CommunicationModel.CD, receiver.tid(), new Val(1));
        MpProcess.Handle child =
                MpProcess.spawn(
                        "child",
                        () -> Channel.send(CommunicationModel.CD, receiver.tid(), new Val(2)));
        child.await();
        receiver.await();

        assertTrue(causalDone[0], "R must have completed both receives");
        assertEquals(1, causal[0], "the parent's earlier cd send must be delivered first");
        assertEquals(2, causal[1], "the spawned child's cd send must be delivered second");

        // Phase 2: concurrent pair — the control.
        final int[] concurrent = {-1, -1};
        final boolean[] concurrentDone = {false};
        MpProcess.Handle control =
                MpProcess.spawn(
                        "R2",
                        () -> {
                            concurrent[0] = Channel.recv(CommunicationModel.CD, Val.class).v();
                            concurrent[1] = Channel.recv(CommunicationModel.CD, Val.class).v();
                            concurrentDone[0] = true;
                        });
        MpProcess.Handle third =
                MpProcess.spawn(
                        "c1",
                        () -> Channel.send(CommunicationModel.CD, control.tid(), new Val(3)));
        MpProcess.Handle fourth =
                MpProcess.spawn(
                        "c2",
                        () -> Channel.send(CommunicationModel.CD, control.tid(), new Val(4)));
        third.await();
        fourth.await();
        control.await();

        assertTrue(concurrentDone[0], "R2 must have completed both receives");
        assertEquals(7, concurrent[0] + concurrent[1], "R2 must receive exactly 3 and 4");
        cdControlSawOrder[concurrent[0] == 3 ? 0 : 1] = true;

        if (runs[3] == ITERATIONS) {
            assertTrue(
                    cdControlSawOrder[0] && cdControlSawOrder[1],
                    "cd-concurrent sends were never reordered, so phase 1's order proves nothing");
        }
    }

    /**
     * One sender's two {@code cd} sends to one destination are delivered in send order — S2 review
     * round 2 major M3, on route A.
     *
     * <p>The mechanism is that {@code TaskClocks.emit} ticks the sender's own component
     * <em>before</em> stamping, so consecutive sends carry strictly increasing clocks and Def. 3.6
     * excludes the second while the first is pending. Without the tick both stamps are equal,
     * neither dominates, and a monitor may observe the sends out of order — the commonest shape in
     * which the defect bites.
     *
     * <p>The discriminating construction is phase 2: the same sender, the same destination, the same
     * two sends, under {@code asyn} instead of {@code cd}. Def. 3.4 constrains nothing, so both
     * orders must be observed across the iterations. That is what shows phase 1's order comes from
     * the {@code cd} rule and not from the wiring only ever producing one interleaving.
     * Probabilistic in phase 2 for a second reason as well — an iteration in which R2's first
     * receive resolves before main's second send has only one candidate and is forced — which only
     * makes the "both orders seen" bar harder to clear, never easier. See the class comment.
     */
    @JmcCheck
    @JmcCheckConfiguration(strategy = "mp-random", numIterations = ITERATIONS)
    @JmcExpectExecutions(ITERATIONS)
    @SuppressWarnings("checkstyle:MethodName")
    public void cd_sameSenderTwoSendsArriveInOrder() {
        runs[4]++;

        // Phase 1: two cd sends from one sender.
        final int[] causal = {-1, -1};
        final boolean[] causalDone = {false};
        MpProcess.Handle receiver =
                MpProcess.spawn(
                        "R",
                        () -> {
                            causal[0] = Channel.recv(CommunicationModel.CD, Val.class).v();
                            causal[1] = Channel.recv(CommunicationModel.CD, Val.class).v();
                            causalDone[0] = true;
                        });
        Channel.send(CommunicationModel.CD, receiver.tid(), new Val(1));
        Channel.send(CommunicationModel.CD, receiver.tid(), new Val(2));
        receiver.await();

        assertTrue(causalDone[0], "R must have completed both receives");
        assertEquals(1, causal[0], "a sender's first cd send must be delivered first");
        assertEquals(2, causal[1], "a sender's second cd send must be delivered second");

        // Phase 2: the same shape under asyn — the control.
        final int[] free = {-1, -1};
        final boolean[] freeDone = {false};
        MpProcess.Handle control =
                MpProcess.spawn(
                        "R2",
                        () -> {
                            free[0] = Channel.recv(CommunicationModel.ASYN, Val.class).v();
                            free[1] = Channel.recv(CommunicationModel.ASYN, Val.class).v();
                            freeDone[0] = true;
                        });
        Channel.send(CommunicationModel.ASYN, control.tid(), new Val(3));
        Channel.send(CommunicationModel.ASYN, control.tid(), new Val(4));
        control.await();

        assertTrue(freeDone[0], "R2 must have completed both receives");
        assertEquals(7, free[0] + free[1], "R2 must receive exactly 3 and 4");
        asynControlSawOrder[free[0] == 3 ? 0 : 1] = true;

        if (runs[4] == ITERATIONS) {
            assertTrue(
                    asynControlSawOrder[0] && asynControlSawOrder[1],
                    "asyn sends from one sender were never reordered, so phase 1 proves nothing");
        }
    }

    // ------------------------------------------------- a blocked main on the @JmcCheck path (M2)

    /**
     * A <b>blocked main</b> ends its iteration and the iteration is counted blocked — on the
     * {@code @JmcCheck} path, which is the only path on which {@code @JmcExpectExecutions} exists.
     *
     * <p>This is S3 review round 3, M2. Contract §3.2(c) (A6), the {@code mp-random} class Javadoc
     * and {@code MpProcess.Handle.await} all say "if main is blocked the iteration ends [and is
     * counted blocked]", and every S3 row that showed it was <em>programmatic</em>
     * ({@code MpBlockingAndGuardTest}), where {@code JmcFunctionalTestTarget} hands the
     * {@code HaltTaskException} to {@code JmcModelChecker.check}'s branch written for it. Under
     * {@code @JmcCheck} the body is invoked reflectively, and {@code JmcTestExecutor} used to wrap
     * every {@code InvocationTargetException} cause in a {@code RuntimeException}: main's
     * {@code HaltTaskException} landed in the checker's catch-all, the error was recorded, and the
     * run failed with {@code JmcCheckerException("java.lang.reflect.InvocationTargetException")} —
     * the same pre-existing defect that keeps {@code GcdTest.runParallelGcdTest} commented out with
     * "TODO: Fix this test". Commit {@code cf6785c3} rethrows a {@code HaltTaskException} or
     * {@code HaltExecutionException} cause as it is; this row is that fix seen from a route-A
     * program.
     *
     * <p>The program: main spawns a child that never sends, then does a blocking {@code recv} on an
     * empty buffer. Nothing can ever answer it, so every iteration ends with
     * {@code SchedulingChoice.blockTask(1)} and {@code child.await()} is never reached. That is safe
     * only because the child terminates on its own — the strategy blocks a parked main
     * <em>last</em> (round-1 B1), so the child has reported {@code FINISH_EVENT} before main is
     * blocked and the abandoned-process rule does not fire.
     *
     * <p>{@code @JmcExpectExecutions(0)} is the claim: the run does not fail, all ten iterations ran
     * ({@code total = 10}) and all ten were counted blocked, so {@code total − blocked = 0}. Before
     * the executor fix this row failed with {@code InvocationTargetException} instead. Two things
     * would also break it: an iteration that was not counted blocked (the count would be too high),
     * and a receive answered rather than blocked.
     *
     * <p>Nothing after main's receive is reachable, so the non-vacuity assertion is made at the
     * <em>top</em> of the body, about the iteration before: every earlier iteration's child must
     * have run to completion. It is a genuine assertion on this path — an {@code AssertionError} out
     * of main is wrapped and recognised by {@code ExceptionUtil.isAssertionError}, and fails the run
     * — and it fires ten times, which is what shows the body really ran. The one thing it cannot
     * rule out is a run in which the body never ran at all ({@code total = 0} would also satisfy the
     * annotation); {@link #blockedMainOnEveryIterationButTheLast_countsTheBlockedOnesAndCompletesTheLast}
     * is the sibling that closes that, by completing its last iteration and asserting the counts
     * from inside it.
     */
    @JmcCheck
    @JmcCheckConfiguration(strategy = "mp-random", numIterations = BLOCKED_ITERATIONS)
    @JmcExpectExecutions(0)
    @SuppressWarnings("checkstyle:MethodName")
    public void blockedMain_endsTheIterationAndIsCountedBlocked() {
        blockedRuns[0]++;
        assertEquals(
                blockedRuns[0] - 1,
                silentChildRuns[0],
                "every earlier iteration's child must have run to completion before its iteration"
                        + " ended");

        MpProcess.Handle child =
                MpProcess.spawn(
                        "silent",
                        () -> {
                            Nondet.chooseBoolean();
                            silentChildRuns[0]++;
                        });

        // Nobody ever sends to main: the strategy blocks this receive in every iteration.
        Channel.recv(CommunicationModel.ASYN);

        child.await(); // unreachable: the receive above never returns
    }

    /**
     * The sibling that makes {@link #blockedMain_endsTheIterationAndIsCountedBlocked} non-vacuous:
     * the same program, blocked on the first nine iterations and <b>completing</b> the tenth, so a
     * main assertion after the receive is reached exactly once and can report what the whole run
     * did.
     *
     * <p>Which iteration this is is decided by the body's own counter, not by the sampler, so the
     * split is deterministic: on the tenth run main first sends {@code Val(9)} to itself, its
     * receive has a candidate and is answered, main awaits the child and asserts. Nine blocked
     * iterations and one completed one give {@code total − blocked = 10 − 9 = 1}, which is
     * {@code @JmcExpectExecutions(1)}.
     *
     * <p>Three assertions, each closing a different hole in the sibling row: the receive read main's
     * own send (so the tenth iteration really completed rather than being blocked too); the body ran
     * ten times (so {@code total = 10}, ruling out the {@code total = 0} reading of
     * {@code @JmcExpectExecutions(0)}); and every iteration's child ran to completion (so no task
     * was left paused across the nine blocked iterations — the B1 property, on the instrumented
     * path). Together with the annotation they pin all three numbers: {@code total = 10},
     * {@code blocked = 9}, {@code executions = 1}.
     */
    @JmcCheck
    @JmcCheckConfiguration(strategy = "mp-random", numIterations = BLOCKED_ITERATIONS)
    @JmcExpectExecutions(1)
    @SuppressWarnings("checkstyle:MethodName")
    public void blockedMainOnEveryIterationButTheLast_countsTheBlockedOnesAndCompletesTheLast() {
        blockedRuns[1]++;
        final boolean last = blockedRuns[1] == BLOCKED_ITERATIONS;

        MpProcess.Handle child =
                MpProcess.spawn(
                        "silent",
                        () -> {
                            Nondet.chooseBoolean();
                            silentChildRuns[1]++;
                        });

        if (last) {
            Channel.send(CommunicationModel.ASYN, MpProcess.self(), new Val(9));
        }
        Val got = Channel.recv(CommunicationModel.ASYN, Val.class);
        child.await();

        assertEquals(9, got.v(), "the last iteration's receive must read main's own send");
        assertEquals(
                BLOCKED_ITERATIONS, blockedRuns[1], "the body must have run once per iteration");
        assertEquals(
                BLOCKED_ITERATIONS,
                silentChildRuns[1],
                "every iteration's child must have run to completion");
    }
}
