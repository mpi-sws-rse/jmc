package org.mpi_sws.jmc.test.det.queue.pQueue;

import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantLock;

public class LockBasedBin implements Bin {

    private final LinkedList<Integer> items;
    private final ReentrantLock lock;

    public LockBasedBin() {
        items = new LinkedList<>();
        lock = new ReentrantLock();
    }

    /**
     * @param item
     */
    @Override
    public void put(int item) {
        lock.lock();
        try {
            items.add(item);
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return
     */
    @Override
    public int get() {
        lock.lock();
        try {
            return items.isEmpty() ? -1 : items.removeFirst();
        } finally {
            lock.unlock();
        }
    }
}
