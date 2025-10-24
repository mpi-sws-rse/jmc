package org.mpi_sws.jmc.test;

import java.util.concurrent.Callable;
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
    public Future increment() {

        Callable<Integer> incrementer = new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                int val = 0;
                lock.lock();
                val = count++;
                lock.unlock();
                return val;
            }
        };

        return executor.submit(incrementer);

    }

    public ExecutorService getExecutor() {
        System.out.println("Executor created in FutureCounter is " + executor.getClass().getName());
        return executor;
    }
}