package org.mpisws.concurrent.programs.atomic.counter;

import org.mpisws.util.concurrent.AtomicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class AtomicCounter {

    public static void main(String[] args) {
        AtomicInteger counter = new AtomicInteger();
        AdderThread adder1 = new AdderThread(counter);
        AdderThread adder2 = new AdderThread(counter);
        adder1.start();
        adder2.start();
        try {
            adder1.join();
            adder2.join();
        } catch (InterruptedException e) {
            System.out.println("Interrupted");
        }
        try {
            System.out.println("Counter value: " + counter.get());
        } catch (JMCInterruptException e) {

        }
    }
}
