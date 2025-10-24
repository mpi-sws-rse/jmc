package org.mpi_sws.jmc.test.det.queue.lbQueue;

import org.mpi_sws.jmc.test.det.queue.Queue;

import java.util.concurrent.locks.ReentrantLock;

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

    public void enq(int x) {
        lock.lock();
        try {
            if (tail - head == CAPACITY) {
                return;
            }
            items[tail % CAPACITY] = x;
            tail++;
        } finally {
            lock.unlock();
        }
    }

    public int deq() {
        lock.lock();
        try {
            if (tail == head) {
                return -1;
            }
            int value = items[head % CAPACITY];
            head++;
            return value;
        } finally {
            lock.unlock();
        }
    }
}
