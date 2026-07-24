package org.mpi_sws.jmc.checker.exceptions;

/**
 * Exception for a concurrency feature JMC does not support.
 *
 * <p>Unlike the rest of this package, it extends {@link RuntimeException} (unchecked). It is raised by
 * the instrumentation agent's visitors when a class uses a {@code java.util.concurrent} construct JMC
 * cannot handle (e.g. an unsupported {@code Executors} factory), and propagates out of the agent's
 * {@code transform} unchanged.
 */
public class JmcUnsupportedFeatureException  extends RuntimeException{
    /**
     * Constructs a new JmcUnsupportedFeatureException with the specified detail message.
     *
     * @param message the detail message
     */
    public JmcUnsupportedFeatureException(String message) {
        super(message);
    }

    /**
     * Constructs a new JmcUnsupportedFeatureException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public JmcUnsupportedFeatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
