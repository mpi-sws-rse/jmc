package org.mpi_sws.jmc.test;

import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class GuavaMoreExecutorCounter {
    private final ExecutorService executorService;
    private final AtomicInteger counter = new AtomicInteger(0);

    public GuavaMoreExecutorCounter(int poolSize) {
        this.executorService = MoreExecutors.getExitingExecutorService((ThreadPoolExecutor) Executors.newFixedThreadPool(poolSize));
        System.out.println("Executor service used is " + executorService.getClass().getName());

    }

    public Future<Integer> submitIncrementTask() {
        System.out.println("Inside submit IncrementTask");

        Callable<Integer> incrementer = new Callable<Integer>() {
            @Override
            public Integer call() {
                System.out.println("CALL start on thread=" + Thread.currentThread().getName()
                        + " threadClass=" + Thread.currentThread().getClass().getName()); // if such accessor exists

                int val = counter.incrementAndGet();
                return val;
            }
        };
        return executorService.submit(incrementer);
    }

    public int getCount() {
        return counter.get();
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void shutdown() {
        executorService.shutdown();
    }

}
