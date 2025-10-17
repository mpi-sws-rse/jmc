package org.mpisws.jmc.test.estimation;

import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.annotations.JmcExpectExecutions;
import org.mpisws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpisws.jmc.strategies.trust.TrustStrategy;
import org.mpisws.jmc.test.readerWriter.Shared;
import org.mpisws.jmc.test.readerWriter.Reader;
import org.mpisws.jmc.test.readerWriter.Incrementor;
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
     * S(T2)    |    R(C)   |     S     |
     * S(T3)    |    F      |     R(C)  |    S
     * J(T1)    |           |     F     |    R(C)
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
     * Inc(n): This program has (n!)^2 distinct execution graphs, where n is the number of threads.
     * the abstract model of this program is like this:
     * Main thread  |  T1       |  T2          | T3
     * ------------------------------------------------
     * W(x)     |             |              |
     * S(T1)    |    S        |              |
     * S(T2)    |    R(C)     |     S        |
     * S(T3)    |    R(C.v)   |     R(C)     |    S
     * J(T1)    |    W(C.v+1) |     R(C.v)   |    R(C)
     * J(T2)    |    F        |     W(C.v+1) |    R(C.v)
     * J(T3)    |             |     F        |    W(C.v+1)
     * F        |             |              |    F
     */
    private void incNProgram(int numThreads) {
        Shared shared = new Shared(0);
        List<Incrementor> threads = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            Incrementor thread = new Incrementor(shared);
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
     * TBA
     */
    private void WRNProgram(int numReaders, int numWriters) {
        Shared shared = new Shared(0);
        List<Reader> readers = new ArrayList<>();
        List<Writer> writers = new ArrayList<>();
        for (int i = 0; i < numReaders; i++) {
            Reader thread = new Reader(shared);
            readers.add(thread);
        }
        for (int i = 0; i < numWriters; i++) {
            Writer thread = new Writer(shared);
            writers.add(thread);
        }
        for (int i = 0; i < numReaders; i++) {
            writers.get(i).start();
        }
        for (int i = 0; i < numWriters; i++) {
            readers.get(i).start();
        }

        for (int i = 0; i < numReaders; i++) {
            try {
                readers.get(i).join();
            } catch (InterruptedException e) {

            }
        }
        for (int i = 0; i < numWriters; i++) {
            try {
                writers.get(i).join();
            } catch (InterruptedException e) {

            }
        }
    }

    private void RWNProgram(int numReaders, int numWriters) {
        Shared shared = new Shared(0);
        List<Reader> readers = new ArrayList<>();
        List<Writer> writers = new ArrayList<>();
        for (int i = 0; i < numReaders; i++) {
            Reader thread = new Reader(shared);
            readers.add(thread);
        }
        for (int i = 0; i < numWriters; i++) {
            Writer thread = new Writer(shared);
            writers.add(thread);
        }
        for (int i = 0; i < numReaders; i++) {
            readers.get(i).start();
        }
        for (int i = 0; i < numWriters; i++) {
            writers.get(i).start();
        }

        for (int i = 0; i < numReaders; i++) {
            try {
                readers.get(i).join();
            } catch (InterruptedException e) {

            }
        }
        for (int i = 0; i < numWriters; i++) {
            try {
                writers.get(i).join();
            } catch (InterruptedException e) {

            }
        }
    }

    /** ----------------------------------------------------*/

    /**
     * R(n) test suite for n \in {2,3,4,5}
     * 1. TruSt model checking
     * 2. DAG-based estimation
     * 3. Fork-Join DAG-based estimation
     * 4. TruSt-based estimation
     * 5. Weighted TruSt-based estimation
     */

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy(loggerTree = true)
    @JmcExpectExecutions(1) // For any n is 1
    public void runRnTrust() {
        readNProgram(2);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000, strategy = "dag-estimation", debug = false)
    public void runRnDagEstimation() {
        readNProgram(4);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000, strategy = "fj-dag-estimation", debug = false)
    public void runRnFjDagEstimation() {
        readNProgram(6);
    }

    // The scheduling policy can be either FIFO or LIFO, both work fine.
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, strategy = "trust-estimation",
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, debug = false)
    public void runRnTrustEstimation() {
        readNProgram(4);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000, strategy = "wg-trust-estimation",
            schedulingPolicy = TrustStrategy.SchedulingPolicy.LIFO, debug = false)
    public void runRnWgTrustEstimation() {
        readNProgram(4);
    }

    /** ----------------------------------------------------*/

    /**
     * Inc(n) test suite for n \in {2,3,4,5}
     * 1. TruSt model checking
     * 2. DAG-based estimation
     * 3. Fork-Join DAG-based estimation
     * 4. TruSt-based estimation
     * 5. Weighted TruSt-based estimation
     */

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, schedulingPolicy = TrustStrategy.SchedulingPolicy.RANDOM)
    @JmcTrustStrategy(loggerTree = true)
    //@JmcExpectExecutions(36) // For input n is (n!)^2
    public void runIncnTrust() {
        incNProgram(3);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 500000, strategy = "dag-estimation", debug = false)
    public void runIncnDagEstimation() {
        incNProgram(3);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 40000, strategy = "fj-dag-estimation", debug = false)
    public void runIncnFjDagEstimation() {
        incNProgram(4);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, strategy = "trust-estimation", debug = false, schedulingPolicy = TrustStrategy.SchedulingPolicy.LIFO)
    public void runIncnTrustEstimation() {
        incNProgram(3);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000, strategy = "wg-trust-estimation", debug = false, schedulingPolicy = TrustStrategy.SchedulingPolicy.LIFO)
    public void runIncnWgTrustEstimation() {
        incNProgram(3);
    }

    /** ----------------------------------------------------*/

    /**
     * RW(n) test suite for n \in {2,3,4,5}
     * 1. TruSt model checking
     * 2. DAG-based estimation
     * 3. Fork-Join DAG-based estimation
     * 4. TruSt-based estimation
     * 5. Weighted TruSt-based estimation
     */

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000, schedulingPolicy = TrustStrategy.SchedulingPolicy.RANDOM)
    @JmcTrustStrategy(loggerTree = true)
    public void runRWNTrust() {
        RWNProgram(3, 3);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 50000, strategy = "dag-estimation", debug = false)
    public void runRWNnDagEstimation() {
        RWNProgram(3, 3);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 40000, strategy = "fj-dag-estimation", debug = false)
    public void runRWNnFjDagEstimation() {
        RWNProgram(4, 4);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 5000, strategy = "trust-estimation", debug = false, schedulingPolicy = TrustStrategy.SchedulingPolicy.LIFO)
    public void runRWNnTrustEstimation() {
        RWNProgram(1, 2);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000, strategy = "wg-trust-estimation", debug = false, schedulingPolicy = TrustStrategy.SchedulingPolicy.LIFO)
    public void runRWNnWgTrustEstimation() {
        RWNProgram(3, 3);
    }

    /** ----------------------------------------------------*/

    /**
     * WR(n) test suite for n \in {2,3,4,5}
     * 1. TruSt model checking
     * 2. DAG-based estimation
     * 3. Fork-Join DAG-based estimation
     * 4. TruSt-based estimation
     * 5. Weighted TruSt-based estimation
     */

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy(loggerTree = true)
    //@JmcExpectExecutions(36) // For input n is n!
    public void runWRNTrust() {
        WRNProgram(3, 3);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 50000, strategy = "dag-estimation", debug = false)
    public void runWRNnDagEstimation() {
        WRNProgram(3, 3);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 40000, strategy = "fj-dag-estimation", debug = false)
    public void runWRNnFjDagEstimation() {
        WRNProgram(4, 4);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10, strategy = "trust-estimation", debug = false, schedulingPolicy = TrustStrategy.SchedulingPolicy.LIFO)
    public void runWRNnTrustEstimation() {
        WRNProgram(10, 10);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000, strategy = "wg-trust-estimation", debug = false, schedulingPolicy = TrustStrategy.SchedulingPolicy.LIFO)
    public void runWRNnWgTrustEstimation() {
        WRNProgram(3, 3);
    }
}
