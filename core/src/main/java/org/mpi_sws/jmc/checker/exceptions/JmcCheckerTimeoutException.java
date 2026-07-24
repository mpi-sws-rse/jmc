package org.mpi_sws.jmc.checker.exceptions;

/**
 * Exception representing a JMC checker timeout.
 *
 * <p>A {@link JmcCheckerException} subtype meant for when a run exceeds its configured timeout.
 *
 * <p><strong>Note:</strong> this type is defined but <em>not currently used</em> — {@link
 * org.mpi_sws.jmc.checker.JmcModelChecker} signals a timeout internally with {@code
 * HaltCheckerException.timeout()} and finalizes the report rather than throwing this exception.
 */
public class JmcCheckerTimeoutException extends JmcCheckerException {
    /**
     * Constructs a new JmcCheckerTimeoutException with the specified detail message.
     *
     * @param message the detail message
     */
    public JmcCheckerTimeoutException(String message) {
        super(message);
    }
}
