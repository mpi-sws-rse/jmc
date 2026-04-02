package org.mpi_sws.jmc.test.det.queue.pQueue.tree;

import java.util.concurrent.atomic.AtomicInteger;

public class Counter {

    private final AtomicInteger value;

    public Counter() {
        value = new AtomicInteger(0);
    }

    public void getAndIncrement() {
        value.getAndIncrement();
    }

    public int boundedGetAndIncrement() {
        while (true) {
            int current = value.get();
            if (value.compareAndSet(current, current - 1)) {
                return current;
            }
        }
    }
}
