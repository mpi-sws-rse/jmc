package org.mpisws.util.concurrent;

import org.mpisws.runtime.JmcRuntime;
import org.mpisws.symbolic.SymbolicBoolean;
import org.mpisws.symbolic.SymbolicOperation;

public class Utils {

    private Utils() {}

    public static void assume(boolean b) throws JMCInterruptException {
        JmcRuntime.concreteAssume(Thread.currentThread(), b);
        if (!b) {
            JmcRuntime.AssumeBlocked(Thread.currentThread());
            JmcRuntime.isExecutionBlocked = true;
            throw new JMCInterruptException();
        }
    }

    public static void assume(SymbolicOperation op) throws JMCInterruptException {
        boolean b = JmcRuntime.symbolicAssume(Thread.currentThread(), op);
        JmcRuntime.waitRequest(Thread.currentThread());
        if (!b) {
            JmcRuntime.AssumeBlocked(Thread.currentThread());
            JmcRuntime.isExecutionBlocked = true;
            throw new JMCInterruptException();
        }
    }

    public static void assume(SymbolicBoolean b) throws JMCInterruptException {
        throw new JMCInterruptException();
    }

    public static void assertion(SymbolicOperation op, String message) {
        JmcRuntime.symAssertOperation(message, op, Thread.currentThread());
    }

    public static void assertion(boolean b, String message) throws JMCInterruptException {
        if (!b) {
            JmcRuntime.assertOperation(message);
            throw new JMCInterruptException();
        }
    }
}
