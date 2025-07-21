package org.mpisws.jmc.strategies;

import org.mpisws.jmc.checker.exceptions.JmcCheckerException;

/**
 * Exception thrown when an invalid strategy is encountered.
 *
 * <p>This exception indicates that the strategy parameter provided is not valid or recognized.
 */
public class JmcInvalidStrategyException extends JmcCheckerException {
    public JmcInvalidStrategyException(String message) {
        super(message);
    }
}
