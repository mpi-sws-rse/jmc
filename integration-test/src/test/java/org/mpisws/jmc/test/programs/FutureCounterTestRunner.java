package org.mpisws.jmc.test.programs;

import java.util.concurrent.Future;

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
            assert calculator.getCount() == 3;
            System.out.println("All futures returned the expected values.");
        } catch (Exception e) {
            System.out.println("An exception occurred: " + e);
        }
    }
}
