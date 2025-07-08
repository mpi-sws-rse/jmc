package org.mpisws.jmc.test.det.stack.agm;

import org.mpisws.jmc.test.det.stack.Stack;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class AGMStack<T> implements Stack<T> {

    public AtomicReferenceArray<T> items;
    public AtomicInteger top;
    public final int CAPACITY;

    public AGMStack(int capacity) {
        CAPACITY = capacity;
        items = new AtomicReferenceArray<>(CAPACITY);
        top = new AtomicInteger(0);
    }

    @Override
    public void push(T item) {
        int i = top.getAndIncrement();
        items.set(i, item);
    }

    @Override
    public T pop() {
        int range = top.get();
        for (int i = range - 1; i > -1; i--) {
            T value = items.getAndSet(i, null);
            if (value != null) {
                return value;
            }
        }
        // stack is empty
        return null;
    }
}
