package org.mpisws.concurrent.programs.nondet.queue.svQueue;

import org.mpisws.concurrent.programs.nondet.queue.Queue;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.Utils;

public class SVQueue implements Queue {

    public SymbolicInteger[] element;
    public int head = 0;
    public int tail = 0;
    public int amount = 0;
    public final int SIZE;

    public SVQueue(int size) {
        this.SIZE = size;
        element = new SymbolicInteger[size];
    }

    public boolean isEmpty() {
        return head == tail;
    }

    public boolean isFull() {
        return amount == SIZE;
    }

    /**
     * @param x
     * @throws JMCInterruptException
     */
    @Override
    public void enq(SymbolicInteger x) throws JMCInterruptException {
        element[tail] = x;
        tail = (tail + 1) % SIZE;
        amount++;
    }

    /**
     * @return
     * @throws JMCInterruptException
     */
    @Override
    public SymbolicInteger deq() throws JMCInterruptException {
        SymbolicInteger x = element[head];
        head = (head + 1) % SIZE;
        amount--;
        return x;
    }
}
