package org.mpisws.jmc.test;

import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.test.readerWriter.Shared;
import org.mpisws.jmc.test.readerWriter.Reader;

import java.util.ArrayList;
import java.util.List;


public class ReadNTest {

    /**
     * This program has 1 distinct execution graph.
     * The abstract model of this program is like this:
     * Main thread  |  T1       |  T2       |  T3
     * ------------------------------------------------
     * W(x)     |           |           |
     * S(T1)    |    S      |           |
     * S(T2)    |    R(x)   |     S     |
     * S(T3)    |    F      |     R(x)  |    S
     * J(T1)    |           |     F     |    R(x)
     * J(T2)    |           |           |    F
     * J(T3)    |           |           |
     * F        |           |           |
     */
    private void readNProgram() {
        Shared shared = new Shared(0);
        List<Reader> threads = new ArrayList<>();
        int numThreads = 3;
        for (int i = 0; i < numThreads; i++) {
            Reader thread = new Reader(shared);
            threads.add(thread);
        }
        for (int i = 0; i < numThreads; i++) {
            threads.get(i).start();
        }

        for (int i = 0; i < numThreads; i++) {
            try {
                threads.get(i).join();
            } catch (InterruptedException e) {

            }
        }
    }

    // Running with JMC using the trust strategy.
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1, strategy = "estimation", debug = false)
    public void runEstimationReadNTest() {
        readNProgram();
    }
}
