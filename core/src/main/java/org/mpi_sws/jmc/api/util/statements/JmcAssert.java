package org.mpi_sws.jmc.api.util.statements;

import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.api.symbolic.bool.JmcBooleanFormula;

/**
 * Assertion statements for use in JMC tests.
 *
 * <p>The concrete-boolean overloads throw an {@link AssertionError} when the condition is false —
 * this is the checker's "bug found" path (the model checker records the offending trace and reports
 * the failure). The {@link org.mpi_sws.jmc.api.symbolic.bool.JmcBooleanFormula} overloads instead
 * report a {@code SYMB_ASSERT_EVENT}, yield to obtain a concrete boolean result from the (symbolic)
 * strategy, and then assert that result.
 */
public class JmcAssert {

    /**
     * Asserts that a concrete condition holds, throwing an {@link AssertionError} otherwise.
     *
     * @param condition the condition that must be {@code true}
     */
    public static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Assertion failed");
        }
    }

    /**
     * Asserts that a concrete condition holds, throwing an {@link AssertionError} with the given
     * message otherwise.
     *
     * @param condition the condition that must be {@code true}
     * @param message the failure message
     */
    public static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /**
     * Asserts a symbolic boolean formula.
     *
     * <p>Reports a {@code SYMB_ASSERT_EVENT} carrying the formula and yields; the (symbolic) strategy
     * resolves it to a concrete boolean, which is then asserted via {@link #check(boolean)}.
     *
     * @param formula the symbolic boolean formula that must hold
     */
    public static void check(JmcBooleanFormula formula) {
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.SYMB_ASSERT_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("booleanFormula", formula)
                        .build();
        boolean result = JmcRuntime.updateEventAndYield(event);
        check(result);
    }

    /**
     * Asserts a symbolic boolean formula, reporting the given message on failure.
     *
     * <p>Like {@link #check(JmcBooleanFormula)} but the resolved result is asserted via {@link
     * #check(boolean, String)} with {@code message}.
     *
     * @param formula the symbolic boolean formula that must hold
     * @param message the failure message
     */
    public static void check(JmcBooleanFormula formula, String message) {
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.SYMB_ASSERT_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("booleanFormula", formula)
                        .build();
        boolean result = JmcRuntime.updateEventAndYield(event);
        check(result, message);
    }
}
