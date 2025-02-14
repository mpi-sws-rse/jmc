package org.mpisws.concurrent.programs.atomic.counter;

import org.mpisws.util.concurrent.JmcAtomicInteger;

public class AtomicCounter {

    public static void main(String[] args) {
        JmcAtomicInteger counter = new JmcAtomicInteger();
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
        assert counter.get() == 1;
        System.out.println("Counter value: " + counter.get());
    }
}
