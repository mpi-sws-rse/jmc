package org.mpisws.jmc.checker;

/**
 * A functional test target for JMC that allows invoking a target method.
 *
 * <p>This class implements the {@link JmcTestTarget} interface and provides a way to invoke a
 * target method with a specified name.
 */
public class JmcFunctionalTestTarget implements JmcTestTarget {

    private final String name;

    private final Target target;

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
