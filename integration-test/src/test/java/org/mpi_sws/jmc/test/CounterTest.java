package org.mpi_sws.jmc.test;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.annotations.JmcExpectExecutions;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CounterTest {

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, debug = true)
    public void testRandomCounter() {
        ParametricCounter counter = new ParametricCounter(2);
        counter.run();
        assertEquals(2, counter.getCounterValue());
    }

    @JmcCheck
    @JmcCheckConfiguration(strategy = "trust", numIterations = 200)
    @JmcExpectExecutions(6)
    public void testTrustCounter() {
        ParametricCounter counter = new ParametricCounter(3);
        counter.run();
        assertEquals(3, counter.getCounterValue());
    }
}
