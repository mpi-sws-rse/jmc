package org.mpi_sws.jmc.test.programs;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.annotations.JmcExpectExecutions;
import org.mpi_sws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpi_sws.jmc.test.atomic.counter.Adder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicCounterTest {

    private void atomicCounterTest(int length) {
        AtomicInteger counter = new AtomicInteger();
        List<Adder> adders = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            Adder adder = new Adder(counter);
            adders.add(adder);
        }

        for (Adder adder : adders) {
            adder.start();
        }

        for (Adder adder : adders) {
            try {
                adder.join();
            } catch (InterruptedException e) {
            }
        }
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy
    @JmcExpectExecutions(5040) // For input 7
    public void runAtomicCounterTest() {
        atomicCounterTest(7);
    }
}
