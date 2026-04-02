package org.mpi_sws.jmc.test.programs;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FutureCounterListTest {
    public static void main(String[] args) {
    AtomicInteger counter = new AtomicInteger();
    ExecutorService executor = Executors.newFixedThreadPool(2);
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
    assertEquals(3, counter.get());



    }

}
