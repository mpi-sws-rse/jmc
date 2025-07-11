package org.mpisws.jmc.test;

import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.annotations.JmcExpectExecutions;

public class CounterTest {

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = true)
    public void testRandomCounter() {
        ParametricCounter counter = new ParametricCounter(2);
        counter.run();
        assert counter.getCounterValue() == 2;
    }

    @JmcCheck
    @JmcCheckConfiguration(strategy = "trust", numIterations = 200)
    @JmcExpectExecutions(120)
    public void testTrustCounter() {
        ParametricCounter counter = new ParametricCounter(5);
        counter.run();
        assert counter.getCounterValue() == 5;
    }
}
