package org.mpisws.jmc.test;

import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.test.readN.ReadN;


public class ReadNTest {

    // Running with JMC using the trust strategy.
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1, debug = true, strategy = "estimation")
    public void runEstimationReadNTest() {
        ReadN.main(new String[]{});
    }
}
