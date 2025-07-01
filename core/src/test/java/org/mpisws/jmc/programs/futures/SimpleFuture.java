package org.mpisws.jmc.programs.futures;

import org.mpisws.jmc.api.util.concurrent.JmcExecutorService;
import org.mpisws.jmc.api.util.concurrent.JmcReentrantLock;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class SimpleFuture {
    /** A calculator that increments a count. */
    public static class CountIncrementorCalculator {
        ExecutorService executor = new JmcExecutorService(1);
        JmcReentrantLock lock = new JmcReentrantLock();
        int count = 0;

        public int getCount() {
            try {
                lock.lock();
                return count;
            } finally {
                lock.unlock();
            }
        }

        public Future<Integer> increment() {
            return executor.submit(
                    () -> {
                        int val = 0;
                        lock.lock();
                        val = count++;
                        lock.unlock();
                        return val;
                    });
        }
    }

    public static void main(String[] args) {
        CountIncrementorCalculator calculator = new CountIncrementorCalculator();
        Future<Integer> future1 = calculator.increment();
        Future<Integer> future2 = calculator.increment();
        Future<Integer> future3 = calculator.increment();
        try {
            future1.get();
            future2.get();
            future3.get();
            assert calculator.getCount() == 3;
            System.out.println("All futures returned the expected values.");
        } catch (Exception e) {
            System.out.println("An exception occurred: " + e);
        }
    }
}
