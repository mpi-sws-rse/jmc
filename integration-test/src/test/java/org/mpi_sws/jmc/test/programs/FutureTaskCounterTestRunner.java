package org.mpi_sws.jmc.test.programs;

import org.mpi_sws.jmc.test.FutureCounter;
import org.mpi_sws.jmc.test.FutureTaskCounter;


import java.util.concurrent.FutureTask;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class  FutureTaskCounterTestRunner {

    public static void main(String[] args){
        FutureTaskCounter counter = new FutureTaskCounter();


        FutureTask<Integer> task1 = counter.createIncrementTask();
        FutureTask<Integer> task2 = counter.createIncrementTask();

        boolean rawFutureDetected = false;

        if (task1.getClass().getName().contains("FutureTask")) {
            rawFutureDetected = true;
        }
        if (task2.getClass().getName().contains("FutureTask")) {
            rawFutureDetected = true;
        }

        // Assert that we correctly detected unsupported usage
        assertTrue(rawFutureDetected, "Detected raw FutureTask allocations — unsupported in JMC");

    }
}
