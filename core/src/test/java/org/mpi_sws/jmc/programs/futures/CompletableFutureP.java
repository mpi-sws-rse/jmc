package org.mpi_sws.jmc.programs.futures;

import org.mpi_sws.jmc.api.util.concurrent.JmcCompletableFuture;
import org.mpi_sws.jmc.api.util.concurrent.JmcReentrantLock;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompletableFutureP {
    public static class CountIncrementorCalculator {
        JmcReentrantLock lock = new JmcReentrantLock();
        public int count = 0;

        public int getCount() {
            int val = 0;
            lock.lock();
            val = count;
            lock.unlock();
            return val;
        }

        public int increment() {
            int val = 0;
            lock.lock();
            val = count++;
            lock.unlock();
            return val;
        }
    }

    public static void main(String[] args) {
        CountIncrementorCalculator calculator = new CountIncrementorCalculator();
        CompletableFuture<Integer> future = JmcCompletableFuture.supplyAsync(calculator::increment)
                .thenApply((result) -> calculator.increment())
                .thenApply((result) -> calculator.increment());
        try {
            assertTrue(future instanceof JmcCompletableFuture);
            assertEquals(3, future.get());
            System.out.println("All futures returned the expected values.");
        } catch (Exception e) {
            System.out.println("An exception occurred: " + e);
            throw new RuntimeException(e);
        }
    }
}
