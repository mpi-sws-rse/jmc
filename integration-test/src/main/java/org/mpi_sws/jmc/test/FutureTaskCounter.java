package org.mpi_sws.jmc.test;

import java.util.concurrent.FutureTask;
import java.util.concurrent.locks.ReentrantLock;

public class FutureTaskCounter {
    private final ReentrantLock lock = new ReentrantLock();
    private int count = 0;

    public int getCount() {
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }

    public FutureTask<Integer> createIncrementTask() {
        return new FutureTask<>(() -> {
            lock.lock();
            try {
                int val = ++count;
                return val;
            } finally {
                lock.unlock();
            }
        });
    }
}
