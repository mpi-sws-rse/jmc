package org.mpi_sws.jmc.test.det.queue.hwQueue;

import org.mpi_sws.jmc.test.det.queue.Queue;

import java.util.concurrent.atomic.AtomicInteger;

public class HWQueue implements Queue {
    public AtomicInteger[] items;
    public AtomicInteger tail;
    public final int CAPACITY;

    public HWQueue(int capacity) {
        CAPACITY = capacity;
        items = new AtomicInteger[CAPACITY];
        for (int i = 0; i < CAPACITY; i++) {
            items[i] = new AtomicInteger(-1);
        }
        tail = new AtomicInteger(0);
    }

    public void enq(int x) {
        int t = tail.getAndIncrement();
        items[t].set(x);
    }

    public int deq() {
        // Unwinding the loop for one iteration
        int range = tail.get();
        for (int i = 0; i < range; i++) {
            int value = items[i].getAndSet(-1);
            if (value != -1) {
                return value;
            }
        }
        return -1;
    }
}
