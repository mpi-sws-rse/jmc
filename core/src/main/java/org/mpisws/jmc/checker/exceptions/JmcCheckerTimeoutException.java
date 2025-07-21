package org.mpisws.jmc.checker.exceptions;

/**
 * Exception class for JMC checker timeout errors.
 *
 * <p>This exception is thrown when the JMC checker exceeds the configured timeout limit during
 * execution.
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
