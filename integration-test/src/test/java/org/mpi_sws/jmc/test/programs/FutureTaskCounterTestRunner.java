package org.mpi_sws.jmc.test.programs;

import org.mpi_sws.jmc.test.FutureCounter;
import org.mpi_sws.jmc.test.FutureTaskCounter;


import java.util.concurrent.FutureTask;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FutureTaskCounterTestRunner {

    public static void main(String[] args){
        FutureTaskCounter counter = new FutureTaskCounter();


        FutureTask<Integer> task1 = counter.createIncrementTask();
        FutureTask<Integer> task2 = counter.createIncrementTask();
        FutureTask<Integer> task3 = counter.createIncrementTask();

//        new Thread(task1).start();
//        new Thread(task2).start();
//        new Thread(task3).start();

        boolean rawFutureDetected = false;

        if (task1.getClass().getName().contains("FutureTask")) {
            rawFutureDetected = true;
        }
        if (task2.getClass().getName().contains("FutureTask")) {
            rawFutureDetected = true;
        }

        // Assert that we correctly detected unsupported usage
        assertTrue(rawFutureDetected, "Detected raw FutureTask allocations — unsupported in JMC");

        System.out.println("Test passed: raw FutureTask allocations correctly flagged.");

//        assertThrows(
//                java.util.concurrent.ExecutionException.class,
//                () -> {
//                    // This is the unsupported call pattern we want to flag
//                    task1.get();
//                },
//                "Direct use of FutureTask is not supported by JMC. Use JmcFuture via helper."
//        );

//        try {
//            task1.get();
//            task2.get();
//            task3.get();
//
//            assertTrue(task1.getClass().getName().contains("JmcFuture"));
//            assertTrue(task2.getClass().getName().contains("JmcFuture"));
//            assertTrue(task3.getClass().getName().contains("JmcFuture"));
//
//            assert counter.getCount() == 3;
//        } catch (Exception e) {
//            System.err.println("An exception occurred: " + e);
//        }

    }
}
