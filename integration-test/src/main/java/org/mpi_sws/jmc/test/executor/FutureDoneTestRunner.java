package org.mpi_sws.jmc.test.executor;

import org.mpi_sws.jmc.test.FutureIsDone;

public class FutureDoneTestRunner {

    public static void main(String[] args) {
        FutureIsDone futureDone = new FutureIsDone();
        FutureIsDone.Result r = futureDone.runTest();

        //assertions before run
//        if (r.before) {
//            throw new AssertionError("Future.isDone() should be false before run()");
//        }
//        if (!r.afterGet) {
//            throw new AssertionError("Future.isDone() should be true after get()");
//        }
    }
}
