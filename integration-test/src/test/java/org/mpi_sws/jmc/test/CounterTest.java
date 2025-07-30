package org.mpi_sws.jmc.test;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.annotations.JmcExpectExecutions;

public class CounterTest {

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
    public void testRandomCounter() {
        ParametricCounter counter = new ParametricCounter(2);
        counter.run();
        assert counter.getCounterValue() == 2;
    }

    @JmcCheck
    @JmcCheckConfiguration(strategy = "trust", numIterations = 200, debug = true)
    @JmcExpectExecutions(120)
    public void testTrustCounter() {
        ParametricCounter counter = new ParametricCounter(5);
        counter.run();
        assert counter.getCounterValue() == 5;
    }
}
