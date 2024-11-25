package org.mpisws.concurrent.programs.atomic.counter;

import org.mpisws.util.concurrent.AtomicInteger;

public class AtomicCounter {

    public static void main(String[] args) {
        AtomicInteger counter = new AtomicInteger();
        AdderThread adder1 = new AdderThread(counter);
        AdderThread adder2 = new AdderThread(counter);
        adder1.start();
        adder2.start();
        try {
            adder1.join1();
            adder2.join1();
        } catch (InterruptedException e) {
            System.out.println("Interrupted");
        }
        System.out.println("Counter value: " + counter.get());
    }
}
