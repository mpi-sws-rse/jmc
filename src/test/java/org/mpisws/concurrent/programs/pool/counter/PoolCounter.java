package org.mpisws.concurrent.programs.pool.counter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class PoolCounter {

    public static void main(String[] args) {
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        List<Future<Integer>> futures = new ArrayList<>();

        Counter counter = new Counter();

        for (int i = 0; i < 4; i++) {
            Callable<Integer> task =
                    () -> {
                        counter.inc();
                        return counter.value;
                    };
            futures.add(executorService.submit(task));
        }

        for (Future<Integer> future : futures) {
            try {
                Integer res = future.get();
                System.out.println("Counter value is: " + res);
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        executorService.shutdown();

        assert (counter.value == 4)
                : " ***The assert did not pass, the counter value is " + counter.value + "***";
        System.out.println(
                "If you see this message, the assert passed. The counter value is "
                        + counter.value);
    }
}
