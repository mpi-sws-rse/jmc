package org.mpisws.concurrent.programs.det.queue.lbQueue;

import org.mpisws.concurrent.programs.det.queue.Queue;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class LBQueue implements Queue {
    public int head, tail;
    public int[] items;
    public final int CAPACITY;
    public final ReentrantLock lock;

    public LBQueue(int capacity) {
        CAPACITY = capacity;
        items = new int[CAPACITY];
        head = 0;
        tail = 0;
        lock = new ReentrantLock();
    }

    public void enq(int x) throws JMCInterruptException {
        lock.lock();
        try {
            if (tail - head == CAPACITY) {
                throw new JMCInterruptException();
            }
            items[tail % CAPACITY] = x;
            tail++;
        } finally {
            lock.unlock();
        }
    }

    public int deq() throws JMCInterruptException {
        lock.lock();
        try {
            if (tail == head) {
                throw new JMCInterruptException();
            }
            int value = items[head % CAPACITY];
            head++;
            return value;
        } finally {
            lock.unlock();
        }
    }
}
