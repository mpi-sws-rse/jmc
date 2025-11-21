package org.mpi_sws.jmc.test.programs;

import org.mpi_sws.jmc.test.GuavaMoreExecutorCounter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class GuavaCounterTestRunner {
    public static void main(String[] args) {
        GuavaMoreExecutorCounter counter = new GuavaMoreExecutorCounter(2);

        try {
            final int tasks = 3;
            List<Future<Integer>> futures = new ArrayList<>(tasks);


            System.out.println("Created  " + tasks + " tasks and futures");

            for (int i = 0; i < tasks; i++) {
                System.out.println("Running task " + i + " of " + tasks);
                Future<Integer> f = counter.submitIncrementTask();
                System.out.println("Received future " + f.getClass().getName());
                futures.add(f);
            }

            System.out.println("ExecutorService used in GuavaCounterTest is " + counter.getExecutorService().getClass().getName());
            //System.out.println("Threadpool used in GuavaCounterTest is " + counter.getUnderlyingPool().getClass().getName());


            //assertTrue(counter.getExecutorService().getClass().getName().contains("JmcExecutorService"));
            //assertTrue(counter.getUnderlyingPool().getClass().getName().contains("JmcThreadPoolExecutor"));

            for (Future<Integer> future : futures) {
                System.out.println("Returned future implementation is: " + future.getClass().getName());
            }

            for (Future<Integer> future : futures) {
                future.get();
            }
            System.out.println("Done with future gets");
            assert counter.getCount() == tasks;
            //counter.shutdown();


        } catch (Exception e) {
            System.out.println("An exception occurred: " + e);
        } //finally{
//        counter.shutdown();
//    }
    }
}
