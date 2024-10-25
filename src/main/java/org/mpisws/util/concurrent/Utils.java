package org.mpisws.util.concurrent;

import org.mpisws.runtime.RuntimeEnvironment;
import org.mpisws.symbolic.SymbolicBoolean;
import org.mpisws.symbolic.SymbolicOperation;


public class Utils {

    private Utils() {
    }

    public static void assume(boolean b) throws JMCInterruptException {
        RuntimeEnvironment.concreteAssume(Thread.currentThread(), b);
        if (!b) {
            RuntimeEnvironment.AssumeBlocked(Thread.currentThread());
            RuntimeEnvironment.isExecutionBlocked = true;
            throw new JMCInterruptException();
        }
    }

    public static void assume(SymbolicOperation op) throws JMCInterruptException {
        boolean b = RuntimeEnvironment.symbolicAssume(Thread.currentThread(), op);
        RuntimeEnvironment.waitRequest(Thread.currentThread());
        if (!b) {
            RuntimeEnvironment.AssumeBlocked(Thread.currentThread());
            RuntimeEnvironment.isExecutionBlocked = true;
            throw new JMCInterruptException();
        }
    }

    public static void assume(SymbolicBoolean b) throws JMCInterruptException {
        throw new JMCInterruptException();
    }

    public static void assertion(SymbolicOperation op, String message) {
        RuntimeEnvironment.symAssertOperation(message, op, Thread.currentThread());
    }
}
