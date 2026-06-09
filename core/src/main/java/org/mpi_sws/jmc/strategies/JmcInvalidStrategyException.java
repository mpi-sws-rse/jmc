package org.mpi_sws.jmc.strategies;

import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException;

/**
 * Exception thrown when an invalid strategy is encountered.
 *
 * <p>This exception indicates that the strategy parameter provided is not valid or recognized.
 */
public class JmcInvalidStrategyException extends JmcCheckerException {
    /**
     * Constructs a new exception with the given message.
     *
     * @param message the detail message describing the invalid strategy
     */
    public JmcInvalidStrategyException(String message) {
        super(message);
    }
}
