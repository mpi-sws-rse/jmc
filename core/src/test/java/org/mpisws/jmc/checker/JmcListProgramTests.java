package org.mpisws.jmc.checker;

import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.programs.det.lists.Client10;
import org.mpisws.jmc.programs.det.lists.Client8;

public class JmcListProgramTests {
    @JmcCheck
    @JmcCheckConfiguration(strategy = "trust", numIterations = 1000)
    void testTrustFineList() {
        Client10.main(new String[] {String.valueOf(5)});
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 150)
    void testRandomFineList() {
        Client10.main(new String[] {String.valueOf(5)});
    }

    @JmcCheck
    @JmcCheckConfiguration(strategy = "trust", numIterations = 1000, debug=true)
    void testTrustLazyList() {
        Client8.main(new String[] {String.valueOf(3)});
    }
}
