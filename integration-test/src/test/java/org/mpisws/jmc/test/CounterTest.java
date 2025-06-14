package org.mpisws.jmc.test;

import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;

public class CounterTest {

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = true)
    public void testRandomCounter() {
        ParametricCounter counter = new ParametricCounter(2);
        counter.run();
        assert counter.getCounterValue() == 2;
    }


    @JmcCheck
    @JmcCheckConfiguration(strategy = "trust", numIterations = 100, debug = true)
    public void testTrustCounter() {
        ParametricCounter counter = new ParametricCounter(2);
        counter.run();
        assert counter.getCounterValue() == 2;
    }
}
