package org.mpisws.concurrent.programs.nondet.queue.hwQueue;

import org.mpisws.concurrent.programs.nondet.queue.Queue;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.AtomicInteger;
import org.mpisws.util.concurrent.AtomicReference;
import org.mpisws.util.concurrent.JMCInterruptException;

public class HWQueue implements Queue {

    public AtomicReference[] items;
    public AtomicInteger tail;
    public final int CAPACITY;
    public final SymbolicInteger NULL = new SymbolicInteger("NULL", false);

    public HWQueue(int capacity) {
        CAPACITY = capacity;
        items = new AtomicReference[CAPACITY];
        for (int i = 0; i < CAPACITY; i++) {
            items[i] = new AtomicReference<SymbolicInteger>(NULL);
        }
        tail = new AtomicInteger(0);
    }

    public void enq(SymbolicInteger x) throws JMCInterruptException {
        int t = tail.getAndIncrement();
        items[t].set(x);
    }

    public SymbolicInteger deq() throws JMCInterruptException {
        /*while (true) {
            int range = tail.get();
            for (int i = 0; i < range; i++) {
                SymbolicInteger value = (SymbolicInteger) items[i].getAndSet(null);
                if (value != null) {
                    return value;
                }
            }
        }*/

        // Unwind the loop for the first iteration
        int range = tail.get();
        for (int i = 0; i < range; i++) {

            SymbolicInteger value = (SymbolicInteger) items[i].getAndSet(NULL);
            if (value != NULL) {
                return value;
            }
        }
        return NULL;
    }
}
