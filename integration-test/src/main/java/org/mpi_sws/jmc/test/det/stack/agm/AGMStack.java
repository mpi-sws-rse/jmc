package org.mpi_sws.jmc.test.det.stack.agm;

import org.mpi_sws.jmc.test.det.stack.Stack;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class AGMStack<T> implements Stack<T> {

    public AtomicReference<T>[] items;
    public AtomicInteger top;
    public final int CAPACITY;

    public AGMStack(int capacity) {
        CAPACITY = capacity;
        items = new AtomicReference[CAPACITY];
        for (int i = 0; i < CAPACITY; i++) {
            items[i] = new AtomicReference<>(null);
        }
        top = new AtomicInteger(0);
    }

    @Override
    public void push(T item) {
        int i = top.getAndIncrement();
        items[i].set(item);
    }

    @Override
    public T pop() {
        int range = top.get();
        for (int i = range - 1; i > -1; i--) {
            T value = items[i].getAndSet(null);
            if (value != null) {
                return value;
            }
        }
        // stack is empty
        return null;
    }
}
