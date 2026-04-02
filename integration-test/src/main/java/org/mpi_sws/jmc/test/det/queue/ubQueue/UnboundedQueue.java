package org.mpi_sws.jmc.test.det.queue.ubQueue;

import org.mpi_sws.jmc.test.det.queue.Queue;

import java.util.concurrent.locks.ReentrantLock;

public class UnboundedQueue implements Queue {

    public final ReentrantLock enqLock, deqLock;
    public volatile Node head, tail;

    public UnboundedQueue() {
        head = new Node(0);
        tail = head;
        enqLock = new ReentrantLock();
        deqLock = new ReentrantLock();
    }

    public void enq(int x) {
        Node node = new Node(x);
        enqLock.lock();
        try {
            tail.next = node;
            tail = node;
        } finally {
            enqLock.unlock();
        }
    }

    public int deq() {
        int result;
        deqLock.lock();
        try {
            if (head.next == null) {
                return -1;
            }
            result = head.next.value;
            head = head.next;
        } finally {
            deqLock.unlock();
        }
        return result;
    }

}
