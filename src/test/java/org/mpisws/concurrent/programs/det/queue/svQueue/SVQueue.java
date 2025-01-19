package org.mpisws.concurrent.programs.det.queue.svQueue;

import org.mpisws.concurrent.programs.det.queue.Queue;
import org.mpisws.util.concurrent.JMCInterruptException;

public class SVQueue implements Queue {

    public int[] element;
    public int head = 0;
    public int tail = 0;
    public int amount = 0;
    public final int SIZE;

    public SVQueue(int size) {
        this.SIZE = size;
        element = new int[size];
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
    public void enq(int x) throws JMCInterruptException {
        if (isFull()) {
            return;
        }
        element[tail] = x;
        tail = (tail + 1) % SIZE;
        amount++;
    }

    /**
     * @return
     * @throws JMCInterruptException
     */
    @Override
    public int deq() throws JMCInterruptException {
        if (isEmpty()) {
            return -1;
        }
        int x = element[head];
        head = (head + 1) % SIZE;
        amount--;
        return x;
    }
}