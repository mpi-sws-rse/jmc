package org.mpi_sws.jmc.test.programs;

import org.mpi_sws.jmc.test.FutureCounter;
import org.mpi_sws.jmc.test.FutureTaskCounter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FutureCounterListTest {
    public static void main(String[] args) throws Exception {
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
        System.out.println(" Returned Future implementation: " + future.getClass().getName());
        futures.add(future);
    }

    for (Future<Integer> future : futures) {
        System.out.println("Waiting for future: " + future);
        future.get();
        System.out.println("Finished future.get");
    }
    assertEquals(3, counter.get());



    }

}
