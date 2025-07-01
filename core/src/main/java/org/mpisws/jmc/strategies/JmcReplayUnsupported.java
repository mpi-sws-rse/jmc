package org.mpisws.jmc.strategies;

import org.mpisws.jmc.checker.exceptions.JmcCheckerException;

public class JmcReplayUnsupported extends JmcCheckerException {
    public JmcReplayUnsupported() {
        super("Replay is not supported by this strategy.");
    }
}
