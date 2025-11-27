package org.mpi_sws.jmc.test.programs;

import org.mpi_sws.jmc.test.FutureCounterPoll;

public class FutureCounterPollTestRunner {
    public static void main(String[] args) {
        FutureCounterPoll test = new FutureCounterPoll();
        FutureCounterPoll.Result r = test.runTest();

        if (!r.allDoneAtEnd) {
            throw new AssertionError("All futures should be doneat the end");
        }

        if (r.iterationsBeforeAllDone <= 0) {
            throw new AssertionError("Polling loop should be run atleast once");
        }
    }
}
