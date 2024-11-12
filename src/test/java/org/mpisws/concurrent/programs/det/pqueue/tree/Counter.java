package org.mpisws.concurrent.programs.det.pqueue.tree;

import org.mpisws.util.concurrent.AtomicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class Counter {

    private final AtomicInteger value;

    public Counter() {
        value = new AtomicInteger(0);
    }

    public void getAndIncrement() throws JMCInterruptException {
        value.getAndIncrement();
    }

    public int boundedGetAndIncrement() throws JMCInterruptException {
        while (true) {
            int current = value.get();
            if (value.compareAndSet(current, current - 1)) {
                return current;
            }
        }
    }
}
