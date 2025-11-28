package org.mpi_sws.jmc.api.util.statements;

import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.symbolic.bool.JmcBooleanFormula;

public class JmcAssert {

    public static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Assertion failed");
        }
    }

    public static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

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
