package org.mpisws.concurrent.programs.det.stack.lockFree.elimination;

import org.mpisws.util.concurrent.JMCInterruptException;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class EliminationArray<V> {

    private final int duration = 10;
    LockFreeExchanger<V>[] exchanger;


    public EliminationArray(int capacity) {
        exchanger = (LockFreeExchanger<V>[]) new LockFreeExchanger[capacity];
        for (int i = 0; i < capacity; i++) {
            exchanger[i] = new LockFreeExchanger<V>();
        }
    }

    public V visit(V value, int range) throws TimeoutException, JMCInterruptException {
        int slot = ThreadLocalRandom.current().nextInt(range);
        return exchanger[slot].exchange(value, duration, TimeUnit.MILLISECONDS);
    }
}