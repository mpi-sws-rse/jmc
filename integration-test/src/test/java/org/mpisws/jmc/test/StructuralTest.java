package org.mpisws.jmc.test;

import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpisws.jmc.test.structural.Counter;

public class StructuralTest {

    private void lambdaCounterTest() {
        Counter counter = new Counter();

        Thread thread1 = new Thread(new Runnable() {
            @Override
            public void run() {
                counter.get();
            }
        });

        Thread thread2 = new Thread(new Runnable() {
            @Override
            public void run() {
                counter.set(1);
            }
        });

        Thread thread3 = new Thread(new Runnable() {
            @Override
            public void run() {
                counter.set(2);
            }
        });


        thread1.start();
        thread2.start();

        try {
            thread1.join();
            thread2.join();
        } catch (InterruptedException e) {
        }
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000, debug = true)
    @JmcTrustStrategy
    public void runLambdaCounterTrustTest() {
        lambdaCounterTest();
    }
}
