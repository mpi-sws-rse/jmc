package org.mpi_sws.jmc.integrations.junit5.descriptors;

/**
 * Marker/execution contract for a runnable JMC test descriptor.
 *
 * <p>A descriptor that implements this interface is a <em>runnable leaf</em>: the engine's tree walk
 * ({@code JmcTestEngine.executeDescriptor}) uses an {@code instanceof} check against this interface to
 * decide whether a descriptor should be run (versus recursed into as a container), then calls {@link
 * #execute()}. The only implementation is {@link JmcMethodTestDescriptor}.
 */
public interface JmcExecutableTestDescriptor {
    /**
     * Runs this test, driving the JMC model checker for the underlying test method.
     *
     * @throws Exception if execution fails; the engine reports it as a failed test result
     */
    void execute() throws Exception;
}
