package org.mpi_sws.jmc.test.executor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.test.FutureCounter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class FutureCounterTestRunner {
    private static final Logger LOGGER = LogManager.getLogger(FutureTaskCounterTestRunner.class);

    public static void main(String[] args) {
        FutureCounter calculator = new FutureCounter();

        Future<Integer> future1 = calculator.increment();
        Future<Integer> future2 = calculator.increment();
        Future<Integer> future3 = calculator.increment();
        ExecutorService executor = calculator.getExecutor();

        try {

            future1.get();
            future2.get();
            future3.get();


            //returned object from counter and get is JmcFuture
            assert ("org.mpi_sws.jmc.api.util.concurrent.JmcFuture" == future1.getClass().getName());
            assert ("org.mpi_sws.jmc.api.util.concurrent.JmcFuture" == future2.getClass().getName());
            assert ("org.mpi_sws.jmc.api.util.concurrent.JmcFuture" == future3.getClass().getName());
            assert calculator.getCount() == 3;
            LOGGER.debug("All futures returned the expected values.");
        } catch (Exception e) {
            System.err.println("An exception occurred: " + e);
        }
    }
}
