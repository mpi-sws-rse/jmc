package org.mpisws.jmc.checker.exceptions;

/**
 * Exception class for JMC checker errors.
 *
 * <p>This exception is thrown when there are issues related to the JMC checker, such as
 * configuration errors or runtime exceptions during the checking process.
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
