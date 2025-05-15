package org.mpisws.jmc.checker;

import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcTimeout;
import org.mpisws.jmc.programs.twophasecommit.Coordinator;

import java.time.temporal.ChronoUnit;

public class TwoPhaseCommitTest {
    @JmcCheck
    @JmcTimeout(value = 10, unit = ChronoUnit.SECONDS)
    void testTPCCommit() {
        Coordinator coordinator = new Coordinator(2);
        coordinator.start();

        // Simulate sending a request to the coordinator
        assert coordinator.acceptRequest(1);

        coordinator.stop();
    }
}
