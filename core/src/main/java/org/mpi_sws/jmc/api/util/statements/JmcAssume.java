package org.mpi_sws.jmc.api.util.statements;

import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;

/**
 * The JmcAssume class provides a method to assert conditions in the JMC runtime environment. If the
 * condition is false, it throws a HaltTaskException, effectively halting the current task.
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
            throw new HaltTaskException(JmcRuntime.currentTask(), "Assumption failed");
        }
    }
}
