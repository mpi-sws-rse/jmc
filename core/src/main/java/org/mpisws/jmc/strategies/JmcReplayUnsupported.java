package org.mpisws.jmc.strategies;

import org.mpisws.jmc.checker.exceptions.JmcCheckerException;

/**
 * Exception thrown when replay is not supported by the current strategy.
 *
 * <p>This exception indicates that the current strategy does not support replay functionality,
 * which may be required for certain operations or analyses.
 */
public class JmcReplayUnsupported extends JmcCheckerException {
    public JmcReplayUnsupported() {
        super("Replay is not supported by this strategy.");
    }
}
