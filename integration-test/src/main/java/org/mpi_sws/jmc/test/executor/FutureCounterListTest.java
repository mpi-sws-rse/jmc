package org.mpi_sws.jmc.test.executor;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class FutureCounterListTest {
    public static void main(String[] args) {
        AtomicInteger counter = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        System.out.println(" Executor implementation: " + executor.getClass().getName());
        //ExecutorService executor = Executors.newSingleThreadExecutor();
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            Callable<Integer> callable = new Callable<Integer>() {
                @Override
                public Integer call() throws Exception {
                    counter.incrementAndGet();
                    return 1;
                }
            };

            Future<Integer> future = executor.submit(callable);
            futures.add(future);
        }

        for (Future<Integer> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        executor.shutdown();
        assert (counter.get() == 3);
    }
}

