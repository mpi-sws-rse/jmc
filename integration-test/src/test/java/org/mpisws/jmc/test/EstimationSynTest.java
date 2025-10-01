package org.mpisws.jmc.test;

import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.annotations.JmcExpectExecutions;
import org.mpisws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpisws.jmc.strategies.trust.TrustStrategy;
import org.mpisws.jmc.test.readerWriter.Shared;
import org.mpisws.jmc.test.readerWriter.Reader;
import org.mpisws.jmc.test.readerWriter.Writer;

import java.util.ArrayList;
import java.util.List;


public class EstimationSynTest {

    /**
     * R(n): This program has 1 distinct execution graph.
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

    /**
     * RW(n): This program has (n!)^2 distinct execution graphs, where n is the number of threads.
     * the abstract model of this program is like this:
     * Main thread  |  T1       |  T2       | T3
     * ------------------------------------------------
     * W(x)     |           |           |
     * S(T1)    |    S      |           |
     * S(T2)    |    R(x)   |     S     |
     * S(T3)    |    W(x)   |     R(x)  |    S
     * J(T1)    |    F      |     W(x)  |    R(x)
     * J(T2)    |           |     F     |    W(x)
     * J(T3)    |           |           |    F
     * F        |           |           |
     */
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

    /** ----------------------------------------------------*/

    /**
     * R(n) test suite for n \in {2,3,4,5}
     * 1. TruSt model checking
     * 2. DAG-based estimation
     * 3. TruSt-based estimation
     */

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy
    @JmcExpectExecutions(1) // For any n is 1
    public void runRnTrust() {
        readNProgram(2);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000, strategy = "dag-estimation", debug = false)
    public void runRnDagEstimation() {
        readNProgram(4);
    }

    // The scheduling policy can be either FIFO or LIFO, both work fine.
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, strategy = "trust-estimation",
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, debug = false)
    public void runRnTrustEstimation() {
        readNProgram(4);
    }

    /** ----------------------------------------------------*/

    /**
     * RW(n) test suite for n \in {2,3,4,5}
     * 1. TruSt model checking
     * 2. DAG-based estimation
     * 3. TruSt-based estimation
     */

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, schedulingPolicy = TrustStrategy.SchedulingPolicy.RANDOM)
    @JmcTrustStrategy
    //@JmcExpectExecutions(36) // For input n is (n!)^2
    public void runTrustReadWriteTest() {
        readWriteNProgram(3);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000, strategy = "dag-estimation", debug = false)
    public void runEstimationReadWriteNTest() {
        readWriteNProgram(3);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, strategy = "trust-estimation", debug = false, schedulingPolicy = TrustStrategy.SchedulingPolicy.LIFO)
    public void runTrustEstimationReadWriteNTest() {
        readWriteNProgram(3);
    }

}
