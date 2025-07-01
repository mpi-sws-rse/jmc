package org.mpisws.jmc.test.programs;

import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.annotations.JmcExpectExecutions;
import org.mpisws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpisws.jmc.test.atomic.counter.Adder;

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
