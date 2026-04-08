package org.mpi_sws.jmc.checker.exceptions;

/**
 * Exception class for JMC unsupported features.
 *
 * <p>This exception is thrown when there are any concurrency related
 * features which are currently unsupported
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
