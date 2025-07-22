package org.mpisws.jmc.test;

import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.test.readerWriter.Shared;
import org.mpisws.jmc.test.readerWriter.Reader;
import org.mpisws.jmc.test.readerWriter.Writer;

import java.util.ArrayList;
import java.util.List;


public class ReadWriteNTest {

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
    private void readNProgram(int numThreads) {
        Shared shared = new Shared(0);
        List<Reader> threads = new ArrayList<>();
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

    private void readWriteNProgram(int numThreads) {
        Shared shared = new Shared(0);
        List<Writer> threads = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            Writer thread = new Writer(shared);
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


    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000, strategy = "estimation", debug = false)
    public void runEstimationReadNTest() {
        readNProgram(4);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10000, strategy = "estimation", debug = false)
    public void runEstimationReadWriteNTest() {
        readWriteNProgram(5);
    }
}
