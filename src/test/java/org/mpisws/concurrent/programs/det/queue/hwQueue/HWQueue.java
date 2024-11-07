package org.mpisws.concurrent.programs.det.queue.hwQueue;

import org.mpisws.concurrent.programs.det.queue.Queue;
import org.mpisws.util.concurrent.AtomicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class HWQueue implements Queue {
    public AtomicInteger[] items;
    public AtomicInteger tail;
    public final int CAPACITY;

    public HWQueue(int capacity) {
        CAPACITY = capacity;
        items = new AtomicInteger[CAPACITY];
        for (int i = 0; i < CAPACITY; i++) {
            items[i] = new AtomicInteger(0);
        }
        tail = new AtomicInteger(0);
    }

    public void enq(int x) throws JMCInterruptException {
        int t = tail.getAndIncrement();
        items[t].set(x);
    }

    public int deq() throws JMCInterruptException {
        while (true) {
            int range = tail.get();
            for (int i = 0; i < range; i++) {
                int value = items[i].getAndSet(0);
                if (value != 0) {
                    return value;
                }
            }
        }
    }
}
