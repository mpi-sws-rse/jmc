package org.mpisws.jmc.strategies;

import org.mpisws.jmc.checker.exceptions.JmcCheckerException;

public class JmcInvalidStrategyException extends JmcCheckerException {
    public JmcInvalidStrategyException(String message) {
        super(message);
    }
}
