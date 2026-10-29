package org.mpi_sws.jmc.test.mp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.annotations.JmcIgnoreInstrumentation;
import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.JmcUnsupportedEventException;
import org.mpi_sws.jmc.test.mp.MpTestMessages.Val;

/**
 * A strategy that cannot answer a message-passing event must fail <em>loudly</em> — L1b and
 * plan/state/mp-contract.md §3.1. There is no fallback path, and there must not be: the first
 * implementation of this API fell back to an unseeded {@code java.util.Random} under {@code trust}
 * and destroyed reproducibility without a trace (rose-state.md F3), while {@code EventFactory}'s
 * {@code return new ArrayList<>()} fallthrough silently dropped any event it did not recognise
 * (F2, L1a).
 *
 * <p>Two guards are pinned here, one per strategy family. The {@code trust} family throws from
 * {@code EventFactory.fromRuntimeEvent}, which now has an explicit case for all three event types.
 * The {@code RandomSchedulingStrategy} family — {@code random}, {@code pct}, {@code fair-pct}, the
 * DAG estimators — throws from {@code RandomSchedulingStrategy.updateEvent} unless the instance
 * implements {@code org.mpi_sws.jmc.strategies.mp.MpAwareStrategy}. A send is the important case
 * for that family: it needs no answer, so without the guard a send-only program would run to
 * completion under {@code pct} and report success while nothing had modelled the message at all.
 *
 * <h2>Why every event here is raised from the main task</h2>
 *
 * <p>An exception raised in a spawned process cannot fail a test. {@code JmcThread.run} catches
 * everything {@code run1()} throws and only logs it (rose-state.md §S0), and an
 * {@code AssertionError} — being an {@code Error} rather than an {@code Exception} — escapes even
 * that catch, to the uncaught-exception handler, which only reports a {@code HALT_EVENT} for a task
 * that has already finished (S3 review round 1, n1). Either way the failure is invisible to the
 * checker, and a test that asserted a throw from a spawned body would pass whether or not the throw
 * happened. Each program below is a single statement executed by the <em>main</em> task, from which
 * the failure propagates out of {@code JmcModelChecker.check} as a {@code JmcCheckerException}.
 *
 * <h2>The exception's <em>type</em> reaches the test (F-C, fixed in S3)</h2>
 *
 * <p>{@code JmcRuntime.updateEvent} used to end with {@code catch (Exception e) { throw
 * HaltExecutionException.error(e.getMessage()); }} — a factory taking a message and <em>no
 * cause</em> — so a {@code JmcUnsupportedEventException} thrown by a strategy was replaced by a
 * {@code HaltExecutionException} carrying only its text and {@code type()} was unreachable from a
 * test. The runtime now <b>chains the cause</b> ({@code HaltExecutionException.error(String,
 * Throwable)}, S3 report F-C), so the typed exception is on the chain and
 * {@link #assertUnsupported} <b>requires</b> it: {@code assertNotNull} on the typed cause and
 * equality on {@code type()}. A regression to message-only wrapping fails here rather than
 * degrading silently. The message-chain assertions are kept beside it because they are what pin
 * <em>which</em> strategy declined the event.
 */
@JmcIgnoreInstrumentation
public class MpApiStrategySupportTest {

    /** The seed, fixed so a failure is reproducible. */
    private static final long SEED = 20260909L;

    /** Where the checker writes its report; the default of {@code JmcCheckerConfiguration}. */
    private static final String REPORT_PATH = "build/test-results/jmc-report";

    /**
     * The iteration budget. One is enough: every claim here is about the first event of the first
     * iteration. An earlier revision needed two, because {@code JmcModelChecker.check} used to
     * replace a {@code HaltExecutionException} raised on the last iteration with "OK" (its
     * {@code finally} block threw {@code HaltCheckerException.ok()} over the exception in flight);
     * that is fixed on {@code fix/checker-iteration-accounting}, cherry-picked onto this branch, and
     * {@code JmcModelCheckerAccountingTest} in {@code core} pins it.
     */
    private static final int ITERATIONS = 1;

    /**
     * Runs {@code program} under the named strategy for {@link #ITERATIONS} iterations.
     *
     * @param strategy the strategy name as {@code SchedulingStrategyFactory} knows it
     * @param name the target's display name
     * @param program the program under test
     * @throws JmcCheckerException whatever the check fails with
     */
    private static void run(String strategy, String name, JmcFunctionalTestTarget.Target program)
            throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .strategyType(strategy)
                        .numIterations(ITERATIONS)
                        .seed(SEED)
                        .reportPath(REPORT_PATH)
                        .build();
        new JmcModelChecker(config).check(new JmcFunctionalTestTarget(name, program));
    }

    /**
     * Asserts that running {@code program} under {@code strategy} fails, loudly, naming
     * {@code expected} and the strategy that declined it.
     *
     * @param expected the event type the strategy must refuse
     * @param strategy the strategy name
     * @param declinedBy the text the strategy identifies itself by in the message
     * @param program the one-statement program that raises the event from the main task
     */
    private static void assertUnsupported(
            JmcRuntimeEvent.Type expected,
            String strategy,
            String declinedBy,
            JmcFunctionalTestTarget.Target program) {
        JmcCheckerException thrown =
                assertThrows(
                        JmcCheckerException.class,
                        () -> run(strategy, expected + "-under-" + strategy, program),
                        expected + " must not be answerable by " + strategy);

        // JmcRuntime.updateEvent now chains the cause (S3 report F-C, fixed by the lead:
        // HaltExecutionException.error(String, Throwable)), so the typed exception is REQUIRED on
        // the chain — a regression to message-only wrapping fails here, not silently.
        JmcUnsupportedEventException typed = firstUnsupportedEventCause(thrown);
        assertNotNull(typed, "a JmcUnsupportedEventException must be on the cause chain (F-C)");
        assertEquals(expected, typed.type(), "the refusal must name the event it refused");

        String chain = messageChain(thrown);
        assertTrue(
                chain.contains(expected.name()),
                "the failure must name " + expected + "; message chain was: " + chain);
        assertTrue(
                chain.contains("is not supported by"),
                "the failure must be the loud L1b refusal, not an incidental error: " + chain);
        assertTrue(
                chain.contains(declinedBy),
                "the failure must name " + declinedBy + " as the strategy that declined: " + chain);
    }

    /**
     * The first {@link JmcUnsupportedEventException} on a cause chain, or {@code null}.
     *
     * @param thrown the exception the checker failed with
     * @return the typed cause, or {@code null} if the type did not survive
     */
    private static JmcUnsupportedEventException firstUnsupportedEventCause(Throwable thrown) {
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (t instanceof JmcUnsupportedEventException typed) {
                return typed;
            }
            if (t.getCause() == t) {
                return null;
            }
        }
        return null;
    }

    /**
     * Every message on a cause chain, concatenated.
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

    // ------------------------------------------------------------------------- the trust family

    /**
     * {@code trust} cannot resolve a receive: it has no buffers, no delivery rule and no notion of a
     * message, so {@code MP_RECV_REQUEST_EVENT} must be refused rather than dropped by
     * {@code EventFactory}'s empty-list fallthrough. Dropping it would leave
     * {@code Channel.request} with no {@code MpDeliveryValue} and the unwrap in the API would throw
     * anyway — but from the wrong side, blaming the API rather than the configuration.
     */
    @Test
    public void mpRecvRequest_underTrust_throwsJmcUnsupportedEventException() {
        assertUnsupported(
                JmcRuntimeEvent.Type.MP_RECV_REQUEST_EVENT,
                "trust",
                "trust",
                () -> Channel.recv(CommunicationModel.ASYN));
    }

    /**
     * {@code trust} cannot answer a nondeterministic choice either. This is the row that matters
     * most historically: the first implementation resolved {@code nondet} under {@code trust} by
     * falling back to an unseeded {@code java.util.Random} (rose-state.md F3), so the run was
     * neither exhaustive nor reproducible and nothing said so.
     */
    @Test
    public void ndEvent_underTrust_throwsJmcUnsupportedEventException() {
        assertUnsupported(
                JmcRuntimeEvent.Type.ND_EVENT, "trust", "trust", () -> Nondet.chooseIndex(2));
    }

    /**
     * A send needs no answer, which is exactly why it must still be refused: an unmodelled send is
     * the one message-passing operation that can pass unnoticed all the way to a green build.
     */
    @Test
    public void mpSendEvent_underTrust_throwsJmcUnsupportedEventException() {
        assertUnsupported(
                JmcRuntimeEvent.Type.MP_SEND_EVENT,
                "trust",
                "trust",
                () -> Channel.send(CommunicationModel.ASYN, MpProcess.self(), new Val(1)));
    }

    // ------------------------------------------------------- the RandomSchedulingStrategy family

    /**
     * The same claim for {@code pct}, which reaches the guard by inheritance:
     * {@code PCTSchedulingStrategy extends RandomSchedulingStrategy} and does not override
     * {@code updateEvent}. Asserting the strategy's own class name in the message is what
     * distinguishes this row from the {@code random} one below — the two would be
     * indistinguishable if the message named only the event.
     */
    @Test
    public void mpSendEvent_underPct_throwsJmcUnsupportedEventException() {
        assertUnsupported(
                JmcRuntimeEvent.Type.MP_SEND_EVENT,
                "pct",
                "PCTSchedulingStrategy",
                () -> Channel.send(CommunicationModel.ASYN, MpProcess.self(), new Val(1)));
    }

    /**
     * And for {@code random} itself — the base of the family, and the strategy
     * {@code "mp-random"} extends. That {@code "mp-random"} passes where {@code "random"} throws is
     * the whole content of the {@code MpAwareStrategy} marker; {@code MpApiWiringTest} runs the same
     * operation under {@code "mp-random"} without incident, which is this row's companion in the
     * other direction.
     */
    @Test
    public void mpSendEvent_underRandom_throwsJmcUnsupportedEventException() {
        assertUnsupported(
                JmcRuntimeEvent.Type.MP_SEND_EVENT,
                "random",
                "RandomSchedulingStrategy",
                () -> Channel.send(CommunicationModel.ASYN, MpProcess.self(), new Val(1)));
    }

    /**
     * The F3 regression guard stated as a claim about the program's control flow, not only about the
     * exception: under {@code random}, {@code Nondet.chooseIndex(2)} must <em>throw</em> rather than
     * return a value from some private generator.
     *
     * <p>{@code random} does answer {@code REACTIVE_EVENT_RANDOM_VALUE} with a seeded value, so a
     * plausible wrong implementation of {@code nondet} — emit that event instead, or call
     * {@code java.util.Random} directly — would run happily here and produce an index. The recorded
     * flag is what rules that out: the statement after the call never executes, and the recorded
     * index is still its sentinel.
     */
    @Test
    public void ndEvent_neverFallsBackToJavaUtilRandom() {
        final int[] returned = {-1};

        assertUnsupported(
                JmcRuntimeEvent.Type.ND_EVENT,
                "random",
                "RandomSchedulingStrategy",
                () -> {
                    returned[0] = Nondet.chooseIndex(2);
                });

        assertEquals(
                -1,
                returned[0],
                "nondet returned an index under a strategy that cannot answer ND_EVENT");
    }

    /**
     * A one-way choice is not a choice: {@code Nondet.chooseIndex(1)} returns {@code 0} without
     * emitting {@code ND_EVENT} at all, so it adds no node to any graph and works under every
     * strategy — including {@code trust}, which refuses the event.
     *
     * <p>The discriminating construction is the pair. Both halves run under {@code trust}, in this
     * method, one after the other: {@code chooseIndex(1)} completes and records {@code 0};
     * {@code chooseIndex(2)} throws. "No event was emitted" is not directly observable from the
     * program, but "no event was emitted" and "an event was emitted" are exactly what separates the
     * two halves under a strategy that refuses the event — so the contrast is the observation.
     */
    @Test
    public void nondet_singletonOptionList_emitsNoEvent() throws JmcCheckerException {
        final int[] returned = {-1};
        final boolean[] completed = {false};

        run(
                "trust",
                "singletonNondetUnderTrust",
                () -> {
                    returned[0] = Nondet.chooseIndex(1);
                    completed[0] = true;
                });

        assertTrue(completed[0], "a singleton choice must not emit an event trust would refuse");
        assertEquals(0, returned[0], "a singleton choice is min(S), index 0");

        // The other half: two options do emit, and trust refuses.
        assertUnsupported(
                JmcRuntimeEvent.Type.ND_EVENT, "trust", "trust", () -> Nondet.chooseIndex(2));
    }
}
