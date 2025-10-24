package org.mpi_sws.jmc.test.programs;

import org.mpi_sws.jmc.test.FutureCounter;

import java.util.concurrent.ExecutorService;

import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExecutorTestRunner {
    public static void main(String[] args) {
    FutureCounter counter = new FutureCounter();
    ExecutorService executor = counter.getExecutor();

    try {
        Future<Integer> f1 = counter.increment();
        Future<Integer> f2 = counter.increment();
        Future<Integer> f3 = counter.increment();

        f1.get(); f2.get(); f3.get();
        System.out.println("Executor used in test is " + executor.getClass().getName());
        // An explaination of why this is mapped to JmcThreadPoolExecutor is
        // because     public static ExecutorService newSingleThreadExecutor() {
        //        return new FinalizableDelegatedExecutorService
        //            (new ThreadPoolExecutor(1, 1,
        //                                    0L, TimeUnit.MILLISECONDS,
        //                                    new LinkedBlockingQueue<Runnable>()));
        //    }
        // uses ThreadpoolExecutor
        assertTrue(executor.getClass().getName().contains("JmcThreadPoolExecutor"));
        assert counter.getCount() == 3;
    } catch (Exception e) {
        System.err.println("An exception occurred: " + e);
    }
    }
}
