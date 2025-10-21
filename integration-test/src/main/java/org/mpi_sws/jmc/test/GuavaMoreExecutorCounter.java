package org.mpi_sws.jmc.test;

import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class GuavaMoreExecutorCounter {
    private final ThreadPoolExecutor underlyingPool;
    private final ExecutorService executorService;
    private final AtomicInteger counter = new AtomicInteger(0);

    public GuavaMoreExecutorCounter(int poolSize) {
        this.underlyingPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(poolSize);
        this.executorService = MoreExecutors.getExitingExecutorService(underlyingPool);
    }

    public Future<Integer> submitIncrementTask() {
        Callable<Integer> task = () -> {
            int newValue = counter.incrementAndGet();
            System.out.println("New counter: " + newValue);
            return newValue;
        };
        return executorService.submit(task);
    }

    public int getCount() {
        return counter.get();
    }

    public ThreadPoolExecutor getUnderlyingPool() {
        return underlyingPool;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void shutdown() {
        executorService.shutdown();
    }

}
