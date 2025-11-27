package org.mpi_sws.jmc.test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FutureCounterPoll {
    public static class Result {
        public int iterationsBeforeAllDone;
        public boolean allDoneAtEnd;
    }

    public Result runTest() {
        ExecutorService exec = Executors.newFixedThreadPool(3);

        List<Future<Integer>> futures = new ArrayList<>();

       // submit tasks
        for (int i = 0; i < 3; i++) {
            int v = i;
            futures.add(
                    exec.submit(() -> {
                        //Thread.sleep(5 + v);
                        return v;
                            }));
        }
        Result r = new Result();

        int iterations = 0;

        while (true) {
            iterations++;

            int numFinished = 0;
            for (Future<Integer> future : futures) {
                if (future.isDone()) {
                    numFinished++;
                }
            }
            if (numFinished == futures.size()) {
                break;
            }
        }
        r.iterationsBeforeAllDone = iterations;
        boolean allDone = true;
        for (Future<Integer> future : futures) {
            if (!future.isDone()) {
                allDone = false;
                break;
            }
        }
        r.allDoneAtEnd = allDone;
        return r;

    }
}
