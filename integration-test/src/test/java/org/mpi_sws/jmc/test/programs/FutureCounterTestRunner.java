package org.mpi_sws.jmc.test.programs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.test.FutureCounter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FutureCounterTestRunner {
    private static final Logger LOGGER = LogManager.getLogger(FutureTaskCounterTestRunner.class);
    public static void main(String[] args) {
        //System.out.println("Starting FutureCounterTestRunner");

        LOGGER.debug("Starting FutureCounterTestRunner");
        FutureCounter calculator = new FutureCounter();
        LOGGER.debug("Created FutureCounter");

        Future<Integer> future1 = calculator.increment();
        LOGGER.debug("Future1 created in test is " + future1.getClass().getName());
        Future<Integer> future2 = calculator.increment();
        LOGGER.debug("Future2 created in test is " + future1.getClass().getName());
        Future<Integer> future3 = calculator.increment();
        LOGGER.debug("Future3 created in test is " + future1.getClass().getName());
        ExecutorService executor = calculator.getExecutor();
        LOGGER.debug("Executor used in test is " + executor.getClass().getName());
        try {

            LOGGER.debug("Starting future1.get()");
            future1.get();
            future2.get();
            future3.get();
            LOGGER.debug("Finished all future.get ");
            //returned object from counter and get is JmcFuture
            assertEquals("org.mpi_sws.jmc.api.util.concurrent.JmcFuture", future1.getClass().getName());
            assertEquals("org.mpi_sws.jmc.api.util.concurrent.JmcFuture", future2.getClass().getName());
            assertEquals("org.mpi_sws.jmc.api.util.concurrent.JmcFuture", future3.getClass().getName());
            assert calculator.getCount() == 3;
            LOGGER.debug("All futures returned the expected values.");
        } catch (Exception e) {
            System.out.println("An exception occurred: " + e);
        }
    }
}
