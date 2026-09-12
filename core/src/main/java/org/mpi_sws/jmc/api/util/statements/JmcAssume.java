package org.mpi_sws.jmc.api.util.statements;

import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;

/**
 * Assumption statement for use in JMC tests.
 *
 * <p>Unlike an assertion, a failed assumption is not a bug: {@link #assume(boolean)} reports an
 * {@code ASSUME_EVENT} and yields, and when the condition is false it throws {@code
 * HaltTaskException.blocked(...)} to block the current task — pruning this execution from the search
 * rather than reporting a failure. It lets a test restrict exploration to schedules that satisfy a
 * precondition.
 */
public class JmcAssume {

    /**
     * Assumes that the given condition is true. If the condition is false, it throws a
     * HaltTaskException, halting the current task.
     *
     * @param condition the condition to assume
     * @throws HaltTaskException if the condition is false
     */
    public static void assume(boolean condition) {
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.ASSUME_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("result", condition)
                        .build();
        JmcRuntime.updateEventAndYield(event);

        if (!condition) {
            throw HaltTaskException.blocked(JmcRuntime.currentTask());
        }
    }
}
