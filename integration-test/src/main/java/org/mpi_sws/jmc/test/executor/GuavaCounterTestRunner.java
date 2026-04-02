package org.mpi_sws.jmc.test.executor;

import org.mpi_sws.jmc.test.GuavaMoreExecutorCounter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GuavaCounterTestRunner {
    public static void main(String[] args) {
        GuavaMoreExecutorCounter counter = new GuavaMoreExecutorCounter(2);

        try {
            final int tasks = 3;
            List<Future<Integer>> futures = new ArrayList<>(tasks);

            for (int i = 0; i < tasks; i++) {

                Future<Integer> f = counter.submitIncrementTask();
                futures.add(f);
            }

            for (Future<Integer> future : futures) {
                future.get();
            }
            assertEquals(tasks, counter.getCount());
            //counter.shutdown();


        } catch (Exception e) {
        }
    }
}
