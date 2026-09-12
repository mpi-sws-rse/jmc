package org.mpi_sws.jmc.checker.exceptions;

/**
 * Base (checked) exception for JMC checker failures.
 *
 * <p>Root of the checker's exception hierarchy — it extends {@link Exception}, so callers must handle
 * it. {@link org.mpi_sws.jmc.checker.JmcModelChecker} throws it (often wrapping a runtime {@code
 * HaltCheckerException} or an unexpected error) when a run fails, and the JUnit descriptors catch it
 * to report a failed test. Subtypes narrow the cause: {@link JmcInvalidConfigurationException},
 * {@link JmcCheckerTimeoutException}, and {@code JmcInvalidStrategyException} (in the {@code
 * strategies} package).
 */
public class JmcCheckerException extends Exception {
    /**
     * Constructs a new JmcCheckerException with the specified detail message.
     *
     * @param message the detail message
     */
    public JmcCheckerException(String message) {
        super(message);
    }

    /**
     * Constructs a new JmcCheckerException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public JmcCheckerException(String message, Throwable cause) {
        super(message, cause);
    }
}
