package org.mpi_sws.jmc.test;

import java.util.concurrent.*;

public class FutureIsDone {
    public static class Result {
        public boolean before;
        public boolean afterSubmitCheck;
        public boolean afterGet;
    }

    public Result runTest()  {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<Integer> f = executor.submit(() -> 42);
        Result r = new Result();

        r.before = f.isDone();

        r.afterSubmitCheck = f.isDone();

        //force completion

        try {
            int v = f.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        r.afterGet = f.isDone();

        //executor.shutdown();

        return r;
    }
}
