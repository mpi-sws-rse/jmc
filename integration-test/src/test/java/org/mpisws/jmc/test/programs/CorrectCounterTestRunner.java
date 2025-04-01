package org.mpisws.jmc.test.programs;

import java.util.concurrent.locks.ReentrantLock;

public class CorrectCounterTestRunner {
    public static void main(String[] args) {
        ReentrantLock lock = new ReentrantLock();
        CounterITest counter = new CounterITest();
        CorrectCounterITest[] threads = new CorrectCounterITest[3];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new CorrectCounterITest(lock, counter);
        }
        for (int i = 0; i < threads.length; i++) {
            threads[i].start();
        }
        for (int i = 0; i < threads.length; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        assert (counter.counter == 3);
        System.out.println("All good!");
    }
}
