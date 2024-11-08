package org.mpisws.concurrent.programs.det.queue.ubQueue;

import org.mpisws.concurrent.programs.det.queue.Queue;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

public class UnboundedQueue implements Queue {

    public final ReentrantLock enqLock, deqLock;
    public volatile Node head, tail;

    public UnboundedQueue() {
        head = new Node(0);
        tail = head;
        enqLock = new ReentrantLock();
        deqLock = new ReentrantLock();
    }

    public void enq(int x) throws JMCInterruptException {
        Node node = new Node(x);
        enqLock.lock();
        try {
            tail.next = node;
            tail = node;
        } finally {
            enqLock.unlock();
        }
    }

    public int deq() throws JMCInterruptException {
        int result;
        deqLock.lock();
        try {
            if (head.next == null) {
                throw new JMCInterruptException();
            }
            result = head.next.value;
            head = head.next;
        } finally {
            deqLock.unlock();
        }
        return result;
    }

}
