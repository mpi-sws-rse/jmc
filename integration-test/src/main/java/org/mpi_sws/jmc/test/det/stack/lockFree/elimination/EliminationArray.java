package org.mpi_sws.jmc.test.det.stack.lockFree.elimination;

public class EliminationArray<V> {

    public final LockFreeExchanger<V>[] exchanger;
    public final int capacity;

    public EliminationArray(int capacity) {
        this.capacity = capacity;
        exchanger = (LockFreeExchanger<V>[]) new LockFreeExchanger[capacity];
        for (int i = 0; i < capacity; i++) {
            exchanger[i] = new LockFreeExchanger<V>();
        }
    }

    public V visit(V value, int slot) {
        return exchanger[slot].exchange(value);
    }
}
