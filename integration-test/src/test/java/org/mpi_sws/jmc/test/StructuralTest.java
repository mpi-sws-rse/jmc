package org.mpi_sws.jmc.test;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpi_sws.jmc.test.structural.Counter;

public class StructuralTest {

    private void lambdaCounterTest() {
        Counter counter = new Counter();

        Thread thread1 =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                counter.get();
                            }
                        });

        Thread thread2 =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                counter.set(1);
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
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy
    public void runLambdaCounterTrustTest() {
        lambdaCounterTest();
    }
}
