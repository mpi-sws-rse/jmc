package org.mpisws.concurrent.programs.nondet.queue;

import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.AtomicInteger;
import org.mpisws.util.concurrent.AtomicReference;
import org.mpisws.util.concurrent.JMCInterruptException;

public class HWQueue implements Queue {

    public AtomicReference[] items;
    public AtomicInteger tail;
    public final int CAPACITY;

    public HWQueue(int capacity) {
        CAPACITY = capacity;
        items = new AtomicReference[CAPACITY];
        for (int i = 0; i < CAPACITY; i++) {
            items[i] = new AtomicReference<SymbolicInteger>(null);
        }
        tail = new AtomicInteger(0);
    }

    public void enq(SymbolicInteger x) throws JMCInterruptException {
        int t = tail.getAndIncrement();
        items[t].set(x);
    }

    public SymbolicInteger deq() throws JMCInterruptException {
        while (true) {
            int range = tail.get();
            for (int i = 0; i < range; i++) {
                SymbolicInteger value = (SymbolicInteger) items[i].getAndSet(null);
                if (value != null) {
                    return value;
                }
            }
        }
    }
}
