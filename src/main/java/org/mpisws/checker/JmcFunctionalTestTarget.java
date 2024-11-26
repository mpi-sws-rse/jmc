package org.mpisws.checker;

public class JmcFunctionalTestTarget implements JmcTestTarget {

    private final String name;

    private Target target;

    public JmcFunctionalTestTarget(String name, Target target) {
        this.name = name;
        this.target = target;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void invoke() {
        target.invoke();
    }

    /** Represents a target for JMC. */
    @FunctionalInterface
    public interface Target {
        void invoke();
    }
}
