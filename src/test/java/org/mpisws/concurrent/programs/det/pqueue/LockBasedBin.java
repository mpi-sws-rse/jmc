package org.mpisws.concurrent.programs.det.pqueue;

import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.ReentrantLock;

import java.util.LinkedList;

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
    public void put(int item) throws JMCInterruptException {
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
    public int get() throws JMCInterruptException {
        lock.lock();
        try {
            return items.isEmpty() ? -1 : items.removeFirst();
        } finally {
            lock.unlock();
        }
    }
}
