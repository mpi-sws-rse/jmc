package org.mpi_sws.jmc.test.programs;

import org.mpi_sws.jmc.test.FutureCounter;

import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FutureCounterTestRunner {
    public static void main(String[] args) {
        FutureCounter calculator = new FutureCounter();
        Future<Integer> future1 = calculator.increment();
        Future<Integer> future2 = calculator.increment();
        Future<Integer> future3 = calculator.increment();
        try {
            future1.get();
            future2.get();
            future3.get();
            assertTrue(future1.getClass().getName().contains("JmcFuture"));
            assertTrue(future2.getClass().getName().contains("JmcFuture"));
            assertTrue(future3.getClass().getName().contains("JmcFuture"));
            assert calculator.getCount() == 3;
            System.out.println("All futures returned the expected values.");
        } catch (Exception e) {
            System.out.println("An exception occurred: " + e);
        }
    }
}
