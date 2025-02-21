package org.mpisws.instrumentation;

import org.mpisws.checker.JmcTestTarget;

/** Represents an instrumented target for JMC. */
public class InstrumentedTarget implements JmcTestTarget {

    // The base target.
    private final JmcTestTarget baseTarget;

    /**
     * Constructs an instrumented target.
     *
     * @param baseTarget The base target.
     */
    public InstrumentedTarget(JmcTestTarget baseTarget) {
        this.baseTarget = baseTarget;
    }

    /**
     * Returns the name of the target, implementing JmcTestTarget interface.
     *
     * @return The name of the target.
     */
    public String name() {
        return "Instrumented_" + baseTarget.name();
    }

    /** Invokes the target, implementing JmcTestTarget interface. */
    public void invoke() {
        baseTarget.invoke();
    }
}
