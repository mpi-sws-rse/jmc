package org.mpi_sws.jmc.programs.atomic.counter;

import org.mpi_sws.jmc.api.util.concurrent.JmcAtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals(1, counter.get());
        System.out.println("Counter value: " + counter.get());
    }
}
