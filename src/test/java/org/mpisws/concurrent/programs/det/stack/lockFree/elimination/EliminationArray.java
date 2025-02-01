package org.mpisws.concurrent.programs.det.stack.lockFree.elimination;

import org.mpisws.util.concurrent.JMCInterruptException;

public class EliminationArray<V> {

    //private final int duration = 1000;
    public final LockFreeExchanger<V>[] exchanger;
    public final int capacity;


    public EliminationArray(int capacity) {
        this.capacity = capacity;
        exchanger = (LockFreeExchanger<V>[]) new LockFreeExchanger[capacity];
        for (int i = 0; i < capacity; i++) {
            exchanger[i] = new LockFreeExchanger<V>();
        }
    }

    public V visit(V value, int slot) throws JMCInterruptException {
        return exchanger[slot].exchange(value);
    }
}
