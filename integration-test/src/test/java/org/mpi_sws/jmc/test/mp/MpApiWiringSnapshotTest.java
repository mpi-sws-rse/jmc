package org.mpi_sws.jmc.test.mp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.annotations.JmcIgnoreInstrumentation;
import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException;
import org.mpi_sws.jmc.mp.consistency.TaskClocks;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Envelope;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.semantics.Buffers;
import org.mpi_sws.jmc.mp.semantics.Pending;
import org.mpi_sws.jmc.strategies.mp.MpRandomSchedulingStrategy;
import org.mpi_sws.jmc.test.mp.MpTestMessages.Val;

import java.util.ArrayList;
import java.util.List;

/**
 * What {@code "mp-random"} <em>holds</em> while a route-A program runs: the pending-message buffers
 * and the per-task clocks that plan/state/mp-contract.md §3 makes the strategy's property and not
 * the API's (law L2 — {@code org.mpi_sws.jmc.api.mp} holds no communication state).
 *
 * <p>These claims are about a strategy <em>instance</em>, so the checker is driven programmatically:
 * {@code JmcCheckerConfiguration.Builder.strategyConstructor} supplies a
 * {@link Snapshotting} subclass of {@link MpRandomSchedulingStrategy}, which is deliberately not
 * final so that {@code resetIteration(int)} can be overridden to copy {@code buffers()} and
 * {@code clocks()} <em>before</em> {@code super} clears them. That is the sanctioned way to observe
 * end-of-iteration state from route A; there is no other, because the strategy is owned by the
 * runtime and the program cannot reach it.
 *
 * <h2>Why this class is exempt from instrumentation</h2>
 *
 * <p>A programmatic JMC test does two things in one method: it runs a program <em>inside</em> the
 * checker, and it asserts about the result <em>outside</em> it, after {@code check} has returned and
 * {@code JmcRuntime.tearDown} has run. Instrumentation does not distinguish the two.
 * {@code JmcReadWriteVisitor} rewrites <em>every</em> field access in an instrumented class into
 * {@code JmcRuntime.updateEvent(...)} followed by {@code JmcRuntime.yield()}, and it skips only the
 * visited class's <em>own</em> final fields ({@code finalFields} is populated from
 * {@code visitField} of the class being visited). A {@code GETSTATIC} of another class's constant —
 * {@code CommunicationModel.ASYN}, say — or a {@code GETFIELD} of another class's final field is
 * therefore instrumented too. Executed after teardown, that {@code yield()} parks the JUnit thread
 * on a {@code CompletableFuture} nothing will ever complete, and the test hangs until the runner's
 * {@code PER_TEST_TIMEOUT} kills it. The S3 report carries the thread dump.
 *
 * <p>The assertions here are exactly such post-{@code check} field reads, so the class carries
 * {@link JmcIgnoreInstrumentation}. Nothing is lost: these programs use only
 * {@code org.mpi_sws.jmc.api.mp}, which reports its own events by calling the runtime directly and
 * needs no rewriting, and every claim below is about state the <em>strategy</em> holds. The agent is
 * exercised, on instrumented programs, by {@code MpApiWiringTest}.
 */
@JmcIgnoreInstrumentation
public class MpApiWiringSnapshotTest {

    /** The seed, fixed so a failure is reproducible. */
    private static final long SEED = 20260909L;

    /** Where the checker writes its report; the default of {@code JmcCheckerConfiguration}. */
    private static final String REPORT_PATH = "build/test-results/jmc-report";

    /**
     * {@code "mp-random"} with an end-of-iteration snapshot.
     *
     * <p>Annotated {@link JmcIgnoreInstrumentation} because it is a scheduling strategy living in an
     * instrumented package: its {@code resetIteration} runs on the task thread inside
     * {@code Scheduler.resetIteration}, and an instrumented field access there would report a
     * runtime event from inside the runtime. The annotation is read per class file, so it applies to
     * this nested class alone and leaves the enclosing test class instrumented.
     */
    @JmcIgnoreInstrumentation
    private static final class Snapshotting extends MpRandomSchedulingStrategy {

        /** The buffers as they stood at the end of each iteration, in iteration order. */
        private final List<Buffers> bufferSnapshots = new ArrayList<>();

        /** The task clocks as they stood at the end of each iteration, in iteration order. */
        private final List<TaskClocks> clockSnapshots = new ArrayList<>();

        Snapshotting() {
            super(SEED, REPORT_PATH);
        }

        @Override
        public void resetIteration(int iteration) {
            bufferSnapshots.add(buffers());
            clockSnapshots.add(clocks());
            super.resetIteration(iteration);
        }
    }

    /**
     * Builds a checker that runs {@code iterations} iterations under {@code strategy}.
     *
     * @param strategy the strategy instance to run under
     * @param iterations the iteration budget
     * @return the checker
     * @throws JmcCheckerException if the configuration is rejected
     */
    private static JmcModelChecker checkerFor(Snapshotting strategy, int iterations)
            throws JmcCheckerException {
        return new JmcModelChecker(
                new JmcCheckerConfiguration.Builder()
                        .numIterations(iterations)
                        .reportPath(REPORT_PATH)
                        .strategyConstructor(config -> strategy)
                        .build());
    }

    /**
     * A send reports {@code MP_SEND_EVENT} and the strategy — never the API — assigns the send its
     * index: the {@code sendIndex} of plan/state/mp-contract.md §2.1, counted per task per
     * iteration.
     *
     * <p>Main sends three messages to a process that never receives, so all three are still pending
     * when the iteration ends and the snapshot shows the whole sequence: {@code src} is main, the
     * indices are {@code 0, 1, 2} in program order, and the values are the ones sent. Running three
     * iterations pins the "per iteration" half — the counter is reset by {@code resetIteration}, so
     * iteration 1 and 2 must start again at 0 rather than continue at 3.
     *
     * <p>Non-vacuous in two directions: the assertion is over the exact list, so a snapshot that
     * happened to be empty (a strategy that cleared the buffers before the snapshot, or a send that
     * emitted nothing) fails on the size check first; and the value carried by each envelope is
     * asserted too, so indices and payloads cannot have been paired up wrongly.
     */
    @Test
    public void send_emitsMpSendEventWithStableSendIndex() throws JmcCheckerException {
        Snapshotting strategy = new Snapshotting();
        final long[] sinkRaw = {-1L};

        checkerFor(strategy, 3)
                .check(
                        new JmcFunctionalTestTarget(
                                "threeSendsToASinkThatNeverReceives",
                                () -> {
                                    MpProcess.Handle sink = MpProcess.spawn("sink", () -> {});
                                    sinkRaw[0] = sink.tid().raw();
                                    Channel.send(CommunicationModel.ASYN, sink.tid(), new Val(1));
                                    Channel.send(CommunicationModel.ASYN, sink.tid(), new Val(2));
                                    Channel.send(CommunicationModel.ASYN, sink.tid(), new Val(3));
                                    sink.await();
                                }));

        assertEquals(3, strategy.bufferSnapshots.size(), "one snapshot per iteration");
        Tid sink = Tid.of(sinkRaw[0]);
        Tid main = Tid.of(1L);

        for (int iteration = 0; iteration < 3; iteration++) {
            List<Pending> pending =
                    strategy.bufferSnapshots.get(iteration).pending(CommunicationModel.ASYN, sink);
            assertEquals(3, pending.size(), "iteration " + iteration + ": three sends are pending");
            for (int index = 0; index < 3; index++) {
                Envelope envelope = pending.get(index).envelope();
                assertEquals(main, envelope.src(), "iteration " + iteration + ": sender");
                assertEquals(
                        index,
                        envelope.sendIndex(),
                        "iteration " + iteration + ": sendIndex restarts at 0 each iteration");
                assertEquals(
                        new Val(index + 1),
                        envelope.value(),
                        "iteration " + iteration + ": the value sent at that index");
            }
        }
    }

    /**
     * A blocking receive with nothing to read <em>parks</em> the task and, when nothing else can
     * run, is blocked — Must's {@code B} label. It does not spin, and it does not return.
     *
     * <p>The whole program is a single blocking receive with no sender anywhere. Three things are
     * asserted: the body was entered once per iteration (so the run really happened), the statement
     * <em>after</em> the receive never executed in any iteration (so no {@code ⊥} leaked into a
     * blocking receive and no default value was invented), and {@code check} returned normally
     * rather than hanging or erroring. The run is given {@code PER_TEST_TIMEOUT} in the environment
     * precisely so a spin or a deadlock shows up as a killed run rather than as a green build.
     *
     * <p>The discriminating half is the second run: the same shape with one message made pending
     * first. There the receive returns and the statement after it executes in every iteration. Both
     * halves count into their own counters, so "never passed the receive" is shown to mean
     * something.
     */
    @Test
    public void blockingRecv_withNothingEverSent_blocksTaskNotSpins() throws JmcCheckerException {
        final int[] entered = {0};
        final int[] passedTheReceive = {0};
        Snapshotting blocking = new Snapshotting();

        checkerFor(blocking, 3)
                .check(
                        new JmcFunctionalTestTarget(
                                "blockingReceiveWithNoSender",
                                () -> {
                                    entered[0]++;
                                    Channel.recv(CommunicationModel.ASYN);
                                    passedTheReceive[0]++;
                                }));

        assertEquals(3, entered[0], "the program body must run once per iteration");
        assertEquals(0, passedTheReceive[0], "a blocking receive with no sender must never return");
        assertEquals(3, blocking.bufferSnapshots.size(), "every iteration must have ended");

        // Control: the same program with a message pending.
        final int[] enteredControl = {0};
        final int[] passedControl = {0};
        Snapshotting running = new Snapshotting();

        checkerFor(running, 3)
                .check(
                        new JmcFunctionalTestTarget(
                                "blockingReceiveWithAPendingMessage",
                                () -> {
                                    enteredControl[0]++;
                                    Channel.send(
                                            CommunicationModel.ASYN, MpProcess.self(), new Val(9));
                                    Val got = Channel.recv(CommunicationModel.ASYN, Val.class);
                                    assertEquals(9, got.v(), "the message read must be the one sent");
                                    passedControl[0]++;
                                }));

        assertEquals(3, enteredControl[0], "the control body must run once per iteration");
        assertEquals(
                3,
                passedControl[0],
                "with a message pending the very same receive must return in every iteration");
    }

    /**
     * {@code "mp-random"} creates its buffers {@link Buffers#withClocks() with clocks}, so a program
     * that uses {@code cd} is stamped correctly without the strategy having to know the program's
     * model set in advance (plan/state/mp-contract.md §3; the clock algebra of
     * plan/state/state.md §5.2). Route A has no state merging, so the clocks cost nothing.
     *
     * <p>Renamed from the contract's earlier identifier by amendment A4.
     *
     * <p>Three assertions, one per consequence: the snapshot's buffers report clocks enabled; the
     * pending {@code cd} message actually carries a stamp (an enabled mode with a {@code null} stamp
     * is impossible — {@code Buffers.emit} throws — so this checks the emission path was taken at
     * all); and the sender's clock is non-empty, i.e. {@code TaskClocks.emit} ticked. The
     * discriminating construction is the first assertion's companion: a
     * {@code Buffers.withoutClocks()} is asserted to report {@code false} from the same accessor, so
     * {@code clocksEnabled()} is shown to distinguish the two modes rather than being constantly
     * true.
     */
    @Test
    public void mpRandom_programUsingCd_createsBuffersWithClocks() throws JmcCheckerException {
        Snapshotting strategy = new Snapshotting();
        final long[] sinkRaw = {-1L};

        checkerFor(strategy, 2)
                .check(
                        new JmcFunctionalTestTarget(
                                "oneCdSendToASinkThatNeverReceives",
                                () -> {
                                    MpProcess.Handle sink = MpProcess.spawn("sink", () -> {});
                                    sinkRaw[0] = sink.tid().raw();
                                    Channel.send(CommunicationModel.CD, sink.tid(), new Val(1));
                                    sink.await();
                                }));

        assertFalse(
                Buffers.withoutClocks().clocksEnabled(),
                "clocksEnabled() must distinguish the two modes");
        assertEquals(2, strategy.bufferSnapshots.size(), "one snapshot per iteration");

        for (int iteration = 0; iteration < 2; iteration++) {
            Buffers buffers = strategy.bufferSnapshots.get(iteration);
            assertTrue(
                    buffers.clocksEnabled(),
                    "iteration " + iteration + ": mp-random must enable clocks unconditionally");

            List<Pending> pending =
                    buffers.pending(CommunicationModel.CD, Tid.of(sinkRaw[0]));
            assertEquals(1, pending.size(), "iteration " + iteration + ": the cd send is pending");
            assertNotNull(
                    pending.get(0).clock(),
                    "iteration " + iteration + ": a cd message must carry its sender's stamp");

            assertFalse(
                    strategy.clockSnapshots.get(iteration).asMap().isEmpty(),
                    "iteration " + iteration + ": emitting must have ticked the sender's clock");
        }
    }
}
