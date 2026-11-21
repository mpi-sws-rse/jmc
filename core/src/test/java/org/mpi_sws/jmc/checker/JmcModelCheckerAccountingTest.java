package org.mpi_sws.jmc.checker;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException;
import org.mpi_sws.jmc.runtime.HaltExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two accounting defects in {@link JmcModelChecker#check}, found while writing the message-passing
 * API's route-A tests (plan/state/log/dev/S3/round-01.report.md, findings F-D and F-E).
 *
 * <p>Both tests run a trivial, non-concurrent target through the real checker with the default
 * {@code random} strategy; no agent is needed.
 */
class JmcModelCheckerAccountingTest {

    private static JmcCheckerConfiguration config(int numIterations) throws JmcCheckerException {
        return new JmcCheckerConfiguration.Builder()
                .strategyType("random")
                .numIterations(numIterations)
                .seed(1L)
                .build();
    }

    /**
     * F-D: a run capped at {@code numIterations = N} executes the target N times and must report
     * N completed iterations — not N − 1. Before the fix the report said 4 for a cap of 5, because
     * the cap path threw {@code HaltCheckerException.ok()} after {@code iteration++} while the
     * total was computed as {@code iteration - 1} (an adjustment correct only for the path where a
     * strategy ends the run from {@code initIteration}).
     */
    @Test
    void check_reportsExactlyTheNumberOfIterationsThatRan() throws JmcCheckerException {
        int[] runs = {0};
        JmcModelCheckerReport report =
                new JmcModelChecker(config(5))
                        .check(new JmcFunctionalTestTarget("count", () -> runs[0]++));
        assertEquals(5, runs[0], "the target ran once per iteration");
        assertEquals(5, report.getTotalIterations(), "the report must count what ran (F-D)");
    }

    /**
     * F-E: a {@link HaltExecutionException} raised on the last iteration must reach the caller.
     * Before the fix the {@code HaltExecutionException} catch branch recorded the error but left
     * {@code successfulIteration} true, so the {@code finally} block threw
     * {@code HaltCheckerException.ok()} — discarding the exception in flight — and {@code check}
     * returned a report as if the run had succeeded. With {@code numIterations = 1} every such
     * failure was swallowed.
     */
    @Test
    void check_propagatesTheHaltExecutionExceptionRaisedOnTheLastIteration() throws JmcCheckerException {
        JmcModelChecker checker = new JmcModelChecker(config(1));
        JmcCheckerException thrown =
                assertThrows(
                        JmcCheckerException.class,
                        () -> checker.check(new JmcFunctionalTestTarget("boom", () -> {
                            throw HaltExecutionException.error("boom");
                        })),
                        "a failure on the last iteration must not be reported as OK (F-E)");
        assertTrue(
                String.valueOf(thrown.getMessage()).contains("boom"),
                "it must be the target's exception that propagated, not another: "
                        + thrown.getMessage());
    }
}
