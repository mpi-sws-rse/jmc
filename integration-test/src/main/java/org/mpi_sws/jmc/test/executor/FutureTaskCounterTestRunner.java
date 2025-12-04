package org.mpi_sws.jmc.test.executor;

import org.mpi_sws.jmc.test.FutureTaskCounter;


import java.util.concurrent.FutureTask;

public class FutureTaskCounterTestRunner {

    public static void main(String[] args) {
        FutureTaskCounter counter = new FutureTaskCounter();


        FutureTask<Integer> task1 = counter.createIncrementTask();
        FutureTask<Integer> task2 = counter.createIncrementTask();

        boolean rawFutureDetected = task1.getClass().getName().contains("FutureTask");

        if (task2.getClass().getName().contains("FutureTask")) {
            rawFutureDetected = true;
        }

        // Assert that we correctly detected unsupported usage
        assert (rawFutureDetected) : "Detected raw FutureTask allocations — unsupported in JMC";
    }
}
