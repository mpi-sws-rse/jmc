package org.mpi_sws.jmc.checker;

/**
 * A functional test target for JMC that allows invoking a target method.
 *
 * <p>This class implements the {@link JmcTestTarget} interface and provides a way to invoke a
 * target method with a specified name.
 */
public class JmcFunctionalTestTarget implements JmcTestTarget {

    /** The target's display name (e.g. the test method name). */
    private final String name;

    /** The action run on each invocation — supplied as a lambda by the caller. */
    private final Target target;

    /**
     * Creates a functional test target.
     *
     * @param name the target's display name
     * @param target the action to run on each {@link #invoke()} (typically a reflective call to the
     *     test method)
     */
    public JmcFunctionalTestTarget(String name, Target target) {
        this.name = name;
        this.target = target;
    }

    /**
     * @return the target's display name
     */
    @Override
    public String name() {
        return name;
    }

    /** Runs the wrapped {@link Target} action once. */
    @Override
    public void invoke() {
        target.invoke();
    }

    /**
     * The action a {@link JmcFunctionalTestTarget} runs — a no-argument, void callback so callers can
     * supply the program under test as a lambda.
     */
    @FunctionalInterface
    public interface Target {
        /** Runs the program under test once. */
        void invoke();
    }
}
