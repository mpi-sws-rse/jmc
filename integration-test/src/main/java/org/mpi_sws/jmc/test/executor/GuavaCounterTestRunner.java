package org.mpi_sws.jmc.test.executor;

import org.mpi_sws.jmc.test.GuavaMoreExecutorCounter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

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
            assert counter.getCount() == tasks;
            //counter.shutdown();


        } catch (Exception e) {
        }
    }
}
