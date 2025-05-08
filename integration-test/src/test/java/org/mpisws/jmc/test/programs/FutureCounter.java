package org.mpisws.jmc.test.programs;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

public class FutureCounter {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    ReentrantLock lock = new ReentrantLock();
    int count = 0;

    public int getCount() {
        try {
            lock.lock();
            return count;
        } finally {
            lock.unlock();
        }
    }

    public Future<Integer> increment() {
        return executor.submit(
                () -> {
                    int val = 0;
                    lock.lock();
                    val = count++;
                    lock.unlock();
                    return val;
                });
    }
}
