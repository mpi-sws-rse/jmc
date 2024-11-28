package org.mpisws.concurrent.programs.nondet.queue.ubQueue;

import org.mpisws.concurrent.programs.nondet.queue.Queue;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class UnboundedQueue implements Queue {

    public ReentrantLock enqLock, deqLock;
    public volatile Node head, tail;

    public UnboundedQueue() {
        SymbolicInteger sym = new SymbolicInteger("sym", false);
        head = new Node(sym);
        tail = head;
        enqLock = new ReentrantLock();
        deqLock = new ReentrantLock();
    }

    public void enq(SymbolicInteger x) throws JMCInterruptException {
        Node node = new Node(x);
        enqLock.lock();
        try {
            tail.next = node;
            tail = node;
        } finally {
            enqLock.unlock();
        }
    }

    public SymbolicInteger deq() throws JMCInterruptException {
        SymbolicInteger result;
        deqLock.lock();
        try {
            if (head.next == null) {
                return null;
            }
            result = head.next.value;
            head = head.next;
        } finally {
            deqLock.unlock();
        }
        return result;
    }
}
