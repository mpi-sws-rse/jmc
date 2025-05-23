package org.mpisws.jmc.checker;

import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.annotations.JmcTimeout;
import org.mpisws.jmc.programs.twophasecommit.Coordinator;
import org.mpisws.jmc.programs.twophasecommit.TwoPhaseCommit;

import java.time.temporal.ChronoUnit;

public class TwoPhaseCommitTest {
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 2, debug = true, strategy = "trust")
    void testTPCCommit() {
        TwoPhaseCommit.main(new String[]{"2"});
    }
}
