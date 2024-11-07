package org.mpisws.concurrent.programs.nondet.queue.lfQueue;

import org.mpisws.concurrent.programs.nondet.queue.Queue;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class LFQueue implements Queue {

    public int head, tail;
    public SymbolicInteger[] items;
    public final int CAPACITY;
    public final ReentrantLock lock;

    public LFQueue(int capacity) {
        CAPACITY = capacity;
        items = new SymbolicInteger[CAPACITY];
        head = 0;
        tail = 0;
        lock = new ReentrantLock();
    }

    public void enq(SymbolicInteger x) throws JMCInterruptException {
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

    public SymbolicInteger deq() throws JMCInterruptException {
        lock.lock();
        try {
            if (tail == head) {
                throw new JMCInterruptException();
            }
            SymbolicInteger value = items[head % CAPACITY];
            head++;
            return value;
        } finally {
            lock.unlock();
        }
    }

}
