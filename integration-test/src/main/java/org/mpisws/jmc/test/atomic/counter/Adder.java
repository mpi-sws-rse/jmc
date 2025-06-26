package org.mpisws.jmc.test.atomic.counter;

import java.util.concurrent.atomic.AtomicInteger;

public class Adder extends Thread {
    private final AtomicInteger counter;

    public Adder(AtomicInteger counter) {
        this.counter = counter;
    }

    @Override
    public void run() {
        counter.compareAndSet(0, 1);
    }
}
