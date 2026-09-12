package org.mpi_sws.jmc.checker.exceptions;

/**
 * Exception for an invalid JMC checker configuration.
 *
 * <p>A {@link JmcCheckerException} subtype thrown while assembling a configuration — for example by
 * {@code JmcCheckerConfiguration.Builder.build()} when neither {@code numIterations} nor a timeout is
 * set, or by {@code JmcDescriptorUtil} for a contradictory {@code @JmcMeasureGraphCoverage} setting.
 */
public class JmcInvalidConfigurationException extends JmcCheckerException {
    /**
     * Constructs a new JmcInvalidConfigurationException with the specified detail message.
     *
     * @param message the detail message
     */
    public JmcInvalidConfigurationException(String message) {
        super(message);
    }
}
