package org.mpi_sws.jmc.strategies.mp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.scheduling.MpFaultValue;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;

import java.util.function.Predicate;

/**
 * The fault latch of {@link MpRandomSchedulingStrategy}, driven event by event without a runtime.
 *
 * <p>The strategy is a plain object: {@code initIteration}, {@code updateEvent}, {@code nextTask} and
 * {@code resetIteration} can be called directly with hand-built {@link JmcRuntimeEvent}s, which is
 * the only way to reach the third delivery tier deterministically — the runtime never lets a
 * program halt main by a {@code HaltTaskException} from a source the strategy cannot intercept
 * <em>and</em> keep a fault latched at the same time (developer round 3, Q2). The other two tiers
 * (main's next event; {@code MpFaultValue} to a parked main) are covered end to end by
 * {@code MpBlockingAndGuardTest} and pinned here as well so the three are documented side by side.
 * The event sequences are the ones the runtime sends: main's {@code START_EVENT} carries
 * {@code startedBy}; a task that halted sends no further event; the checker records the error on
 * the report before it resets (S3 review round 3, M1(c)).
 */
class MpRandomSchedulingStrategyFaultTest {

    private record Msg(int v) implements MpMessage {}

    private static JmcRuntimeEvent event(JmcRuntimeEvent.Type type, long task, Object... kv) {
        JmcRuntimeEvent.Builder b = new JmcRuntimeEvent.Builder().type(type).taskId(task);
        for (int i = 0; i < kv.length; i += 2) {
            b.param((String) kv[i], kv[i + 1]);
        }
        return b.build();
    }

    private static MpRandomSchedulingStrategy fresh(JmcModelCheckerReport report) {
        MpRandomSchedulingStrategy s = new MpRandomSchedulingStrategy(7L, "build/test-results/jmc-report");
        s.initIteration(0, report);
        s.updateEvent(event(JmcRuntimeEvent.Type.START_EVENT, 1L, "startedBy", 1L)); // as JmcRuntime builds it
        s.updateEvent(event(JmcRuntimeEvent.Type.START_EVENT, 2L, "startedBy", 1L));
        return s;
    }

    /**
     * A guard that re-enters the strategy, as an instrumented read inside a guard would; the
     * strategy must throw at the point of detection and latch the fault.
     */
    private static Predicate<MpMessage> reentrant(MpRandomSchedulingStrategy s, long onTask) {
        return m -> {
            s.updateEvent(event(JmcRuntimeEvent.Type.READ_EVENT, onTask));
            return true;
        };
    }

    /** Main parks; task 2 sends; the guard re-enters on task 2's thread — the latch is set. */
    private static MpRandomSchedulingStrategy withLatchedFault(JmcModelCheckerReport report) {
        MpRandomSchedulingStrategy s = fresh(report);
        s.updateEvent(event(JmcRuntimeEvent.Type.MP_RECV_REQUEST_EVENT, 1L,
                "model", CommunicationModel.ASYN, "guard", reentrant(s, 2L), "blocking", true));
        HaltExecutionException atDetection = assertThrows(HaltExecutionException.class, () ->
                s.updateEvent(event(JmcRuntimeEvent.Type.MP_SEND_EVENT, 2L,
                        "model", CommunicationModel.ASYN,
                        "dst", org.mpi_sws.jmc.mp.events.Tid.of(1L), "value", new Msg(1))));
        assertTrue(atDetection.getMessage().contains("instrumented access"), atDetection.getMessage());
        // Task 2 unwinds, as JmcThread.run would after logging the exception.
        s.updateEvent(event(JmcRuntimeEvent.Type.FINISH_EVENT, 2L));
        return s;
    }

    @Test
    void tier1_mainsNextEventRethrowsTheLatchedFault_andTheResetIsSilentOnceTheCheckerRecordedIt() {
        JmcModelCheckerReport report = new JmcModelCheckerReport("build/test-results/jmc-report");
        MpRandomSchedulingStrategy s = withLatchedFault(report);
        // Main is not parked here: it is awaiting task 2, whose finish completes the join.
        HaltExecutionException onMain = assertThrows(HaltExecutionException.class, () ->
                s.updateEvent(event(JmcRuntimeEvent.Type.JOIN_COMPLETE_EVENT, 1L, "joinedTask", 2L)),
                "main's next event must rethrow the latched fault");
        assertTrue(onMain.getMessage().contains("instrumented access"), onMain.getMessage());
        // The exception propagates out of main; the checker records it and resets.
        report.setErrorIteration(0);
        report.setErrorMessage(onMain.getMessage());
        s.resetIteration(0);   // silent: recorded
    }

    @Test
    void directThrowOnMainsOwnThread_isTheDeliveryAndTheResetIsSilentOnceRecorded() {
        JmcModelCheckerReport report = new JmcModelCheckerReport("build/test-results/jmc-report");
        MpRandomSchedulingStrategy s = fresh(report);
        s.updateEvent(event(JmcRuntimeEvent.Type.MP_SEND_EVENT, 2L,
                "model", CommunicationModel.ASYN,
                "dst", org.mpi_sws.jmc.mp.events.Tid.of(1L), "value", new Msg(1)));
        // The guard re-enters on task 1's own thread (a message is pending, so it is evaluated):
        // the throw at detection reaches main directly.
        HaltExecutionException atDetection = assertThrows(HaltExecutionException.class, () ->
                s.updateEvent(event(JmcRuntimeEvent.Type.MP_RECV_REQUEST_EVENT, 1L,
                        "model", CommunicationModel.ASYN, "guard", reentrant(s, 1L), "blocking", false)));
        assertTrue(atDetection.getMessage().contains("instrumented access"));
        report.setErrorIteration(0);
        report.setErrorMessage(atDetection.getMessage());
        s.resetIteration(0);   // silent: delivered once, recorded — no second throw
    }

    @Test
    void faultDeliveredButSwallowedByMain_isRethrownAtReset() {
        JmcModelCheckerReport report = new JmcModelCheckerReport("build/test-results/jmc-report");
        MpRandomSchedulingStrategy s = withLatchedFault(report);
        assertThrows(HaltExecutionException.class, () ->
                s.updateEvent(event(JmcRuntimeEvent.Type.JOIN_COMPLETE_EVENT, 1L, "joinedTask", 2L)));
        // Main caught it and carried on: more main events pass (single-shot delivery), main
        // finishes normally, and the checker records nothing.
        s.updateEvent(event(JmcRuntimeEvent.Type.FINISH_EVENT, 1L));
        HaltExecutionException atReset = assertThrows(HaltExecutionException.class, () -> s.resetIteration(0),
                "a fault the checker never recorded must be reported at the end of the iteration");
        assertTrue(atReset.getMessage().contains("instrumented access"), atReset.getMessage());
        s.resetIteration(0);   // cleared
    }

    @Test
    void tier2_parkedMainIsResumedWithAnMpFaultValueInsteadOfBeingBlocked() {
        JmcModelCheckerReport report = new JmcModelCheckerReport("build/test-results/jmc-report");
        MpRandomSchedulingStrategy s = withLatchedFault(report);
        SchedulingChoice<?> choice = s.nextTask();
        assertNotNull(choice, "main is parked with a fault latched: something must be scheduled");
        assertEquals(1L, choice.getTaskId(), "it is main that is resumed");
        assertTrue(choice.getValue() instanceof MpFaultValue, "…with the fault, not a block: " + choice);
        assertTrue(((MpFaultValue) choice.getValue()).message().contains("instrumented access"));
        assertEquals(0, s.blockedIterations(), "a faulted iteration is not counted as blocked");
        assertEquals(0, report.getBlockedIterations());
        // Main's unwrap throws the fault, the checker records it, and the reset is silent.
        report.setErrorIteration(0);
        report.setErrorMessage(((MpFaultValue) choice.getValue()).message());
        s.resetIteration(0);
    }

    @Test
    void tier3_resetIterationRethrowsTheFaultThatWasNeverDelivered() {
        JmcModelCheckerReport report = new JmcModelCheckerReport("build/test-results/jmc-report");
        MpRandomSchedulingStrategy s = withLatchedFault(report);
        // Main halts by a HaltTaskException the strategy never sees (no event), and the checker
        // goes straight to resetIteration.
        HaltExecutionException atReset = assertThrows(HaltExecutionException.class, () -> s.resetIteration(0),
                "an undelivered fault must not be dropped at the end of the iteration");
        assertTrue(atReset.getMessage().contains("instrumented access"), atReset.getMessage());
        // …and the latch is cleared by the reset.
        s.resetIteration(0);
    }

    @Test
    void abandonedProcess_isRefusedAtResetWhenMainHaltedWithoutAnEvent() {
        JmcModelCheckerReport report = new JmcModelCheckerReport("build/test-results/jmc-report");
        MpRandomSchedulingStrategy s = fresh(report);
        assertEquals(java.util.Set.of(2L), s.liveProcesses());
        HaltExecutionException atReset = assertThrows(HaltExecutionException.class, () -> s.resetIteration(0));
        assertTrue(atReset.getMessage().contains("must be awaited"), atReset.getMessage());
    }

    @Test
    void abandonedProcess_isNotReportedOverAnErrorTheCheckerAlreadyRecorded() {
        JmcModelCheckerReport report = new JmcModelCheckerReport("build/test-results/jmc-report");
        MpRandomSchedulingStrategy s = fresh(report);
        report.setErrorIteration(0);
        report.setErrorMessage("an assertion failed in main");
        s.resetIteration(0);   // must not throw: the real error is in flight
        assertEquals(java.util.Set.of(), s.liveProcesses());
    }

    @Test
    void abandonedProcess_isRefusedOnMainsAssumeFalse() {
        JmcModelCheckerReport report = new JmcModelCheckerReport("build/test-results/jmc-report");
        MpRandomSchedulingStrategy s = fresh(report);
        HaltExecutionException e = assertThrows(HaltExecutionException.class, () ->
                s.updateEvent(event(JmcRuntimeEvent.Type.ASSUME_EVENT, 1L, "result", false)));
        assertTrue(e.getMessage().contains("assume(false)") && e.getMessage().contains("must be awaited"),
                e.getMessage());
        // Control: assume(true) is not a halt.
        MpRandomSchedulingStrategy t = fresh(new JmcModelCheckerReport("build/test-results/jmc-report"));
        t.updateEvent(event(JmcRuntimeEvent.Type.ASSUME_EVENT, 1L, "result", true));
    }
}
