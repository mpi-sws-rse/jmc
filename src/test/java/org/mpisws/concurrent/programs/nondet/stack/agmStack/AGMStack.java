package org.mpisws.concurrent.programs.nondet.stack.agmStack;

import org.mpisws.concurrent.programs.nondet.stack.Stack;
import org.mpisws.util.concurrent.AtomicInteger;
import org.mpisws.util.concurrent.AtomicReferenceArray;
import org.mpisws.util.concurrent.JMCInterruptException;

public class AGMStack<T> implements Stack<T> {

    public AtomicReferenceArray<T> items;
    public AtomicInteger top;
    public final int CAPACITY;

    public AGMStack(int capacity) {
        CAPACITY = capacity;
        items = new AtomicReferenceArray<>(CAPACITY);
        top = new AtomicInteger(0);
    }

    /**
     * @param item
     */
    @Override
    public void push(T item) throws JMCInterruptException {
        int i = top.getAndIncrement();
        items.set(i, item);
    }

    /**
     * @return
     */
    @Override
    public T pop() throws JMCInterruptException {
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
