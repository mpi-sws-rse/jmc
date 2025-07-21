package org.mpisws.jmc.checker.exceptions;

/**
 * Exception class for JMC invalid configuration errors.
 *
 * <p>This exception is thrown when there are issues related to the configuration of the JMC
 * checker, such as missing or invalid settings.
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
