package org.mpisws.util.concurrent;

import org.mpisws.runtime.JmcRuntime;
import org.mpisws.runtime.RuntimeEvent;
import org.mpisws.runtime.RuntimeEventType;

public class Utils {

    private Utils() {}

    public static void assume(boolean b) throws JMCInterruptException {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.ASSUME_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("condition", b)
                        .build();
        JmcRuntime.updateEventAndYield(event);
        if (!b) {
            event =
                    new RuntimeEvent.Builder()
                            .type(RuntimeEventType.ASSUME_BLOCKED_EVENT)
                            .taskId(JmcRuntime.currentTask())
                            .build();
            JmcRuntime.updateEventAndYield(event);
            throw new JMCInterruptException();
        }
    }

    //    TODO: Need to support symbolic operations in the new restructured runtime.
    //    public static void assume(SymbolicOperation op) throws JMCInterruptException {
    //        boolean b = JmcRuntime.symbolicAssume(Thread.currentThread(), op);
    //        JmcRuntime.waitRequest(Thread.currentThread());
    //        if (!b) {
    //            JmcRuntime.AssumeBlocked(Thread.currentThread());
    //            JmcRuntime.isExecutionBlocked = true;
    //            throw new JMCInterruptException();
    //        }
    //    }
    //
    //    public static void assume(SymbolicBoolean b) throws JMCInterruptException {
    //        throw new JMCInterruptException();
    //    }
    //
    //    public static void assertion(SymbolicOperation op, String message) {
    //        JmcRuntime.symAssertOperation(message, op, Thread.currentThread());
    //    }

    public static void assertion(boolean b, String message) throws JMCInterruptException {
        if (!b) {
            RuntimeEvent event =
                    new RuntimeEvent.Builder()
                            .type(RuntimeEventType.ASSERT_EVENT)
                            .taskId(JmcRuntime.currentTask())
                            .param("condition", b)
                            .param("message", message)
                            .build();
            JmcRuntime.updateEventAndYield(event);
            throw new JMCInterruptException();
        }
    }
}
