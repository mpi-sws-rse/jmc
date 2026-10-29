package org.mpi_sws.jmc.integrations.junit5.engine;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;

import java.lang.reflect.Method;

/**
 * {@link JmcTestExecutor#execute} must hand JMC's own control-flow exceptions, raised inside the
 * reflectively invoked test body, to {@code JmcModelChecker.check} as they are — not wrapped in a
 * {@code RuntimeException} that lands in the checker's catch-all and fails the run.
 *
 * <p>Both tests fail on the previous code: a blocked main was reported as
 * {@code JmcCheckerException("java.lang.reflect.InvocationTargetException")}, and a strategy error
 * lost its type and message the same way.
 */
class JmcTestExecutorUnwrapTest {

    private static final int ITERATIONS = 3;

    private static JmcCheckerConfiguration config() throws Exception {
        return new JmcCheckerConfiguration.Builder()
                .numIterations(ITERATIONS)
                .reportPath("build/test-results/jmc-report")
                .build();
    }

    /** A test body whose main task is halted (blocked) every iteration, as JmcAssume.assume(false) does. */
    public void blockedMain() {
        throw HaltTaskException.blocked(1L);
    }

    /** A test body in which a strategy-level error surfaces on the main task. */
    public void erroringMain() {
        throw HaltExecutionException.error("boom");
    }

    private Method body(String name) throws NoSuchMethodException {
        return getClass().getMethod(name);
    }

    @Test
    void haltTaskExceptionInMain_endsTheIterationInsteadOfFailingTheRun() throws Exception {
        JmcModelCheckerReport report = JmcTestExecutor.execute(body("blockedMain"), this, config());
        assertNull(report.getErrorIteration(),
                "a halted main ends its iteration; the run completes without an error"
                        + " (checker's HaltTaskException branch)");
    }

    @Test
    void haltExecutionExceptionInMain_reachesTheCheckerWithItsMessage() throws Exception {
        JmcCheckerException thrown = assertThrows(JmcCheckerException.class,
                () -> JmcTestExecutor.execute(body("erroringMain"), this, config()));
        assertTrue(String.valueOf(thrown.getMessage()).contains("boom"),
                "the strategy error must be the reported error, not InvocationTargetException: "
                        + thrown.getMessage());
    }
}
