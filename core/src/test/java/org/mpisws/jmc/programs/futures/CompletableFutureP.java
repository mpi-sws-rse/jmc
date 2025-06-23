package org.mpisws.jmc.programs.futures;

import org.mpisws.jmc.api.util.concurrent.JmcCompletableFuture;
import org.mpisws.jmc.api.util.concurrent.JmcReentrantLock;

import java.util.concurrent.CompletableFuture;

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
            assert future instanceof JmcCompletableFuture;
            assert future.get() == 3;
            System.out.println("All futures returned the expected values.");
        } catch (Exception e) {
            System.out.println("An exception occurred: " + e);
            throw new RuntimeException(e);
        }
    }
}
