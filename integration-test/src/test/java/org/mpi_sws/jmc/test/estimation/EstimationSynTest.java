package org.mpi_sws.jmc.test.estimation;

import org.junit.jupiter.api.Disabled;
import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.annotations.JmcExpectExecutions;
import org.mpi_sws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpi_sws.jmc.strategies.trust.TrustStrategy;
import org.mpi_sws.jmc.test.bigShot.Str;
import org.mpi_sws.jmc.test.bigShot.T1;
import org.mpi_sws.jmc.test.bigShot.T2;
import org.mpi_sws.jmc.test.det.counter.CCounter;
import org.mpi_sws.jmc.test.det.counter.DecThread;
import org.mpi_sws.jmc.test.det.counter.FCounter;
import org.mpi_sws.jmc.test.det.counter.IncThread;
import org.mpi_sws.jmc.test.det.queue.svQueue.*;
import org.mpi_sws.jmc.test.det.stack.svStack.SVStack;
import org.mpi_sws.jmc.test.readerWriter.Shared;
import org.mpi_sws.jmc.test.readerWriter.Reader;
import org.mpi_sws.jmc.test.readerWriter.Incrementor;
import org.mpi_sws.jmc.test.readerWriter.Writer;
import org.mpi_sws.jmc.test.synth.fibBench.FibShared;
import org.mpi_sws.jmc.test.synth.fibBench.FibThread1;
import org.mpi_sws.jmc.test.synth.fibBench.FibThread2;
import org.mpi_sws.jmc.test.synth.sigma.SigmaShared;
import org.mpi_sws.jmc.test.synth.sigma.SigmaThread;
import org.mpi_sws.jmc.test.synth.singletone.SingletoneShared;
import org.mpi_sws.jmc.test.synth.singletone.SingletoneThread0;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;


public class EstimationSynTest {

    /** ---------------------------------------------------------------------------------------------------------
     *  Synthetic programs for testing the estimation strategies. The programs are designed to have a known number
     *  of distinct execution graphs, which can be used to evaluate the accuracy of the estimation strategies.
     *  ----------------------------------------------------------------------------------------------------------*/

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
        for (int i = 0; i < numWriters; i++) {
            Writer thread = new Writer(shared);
            writers.add(thread);
        }
        for (int i = 0; i < numReaders; i++) {
            Reader thread = new Reader(shared);
            readers.add(thread);
        }
        for (int i = 0; i < numWriters; i++) {
            writers.get(i).start();
        }
        for (int i = 0; i < numReaders; i++) {
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

    /**
     * TBA
     */
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

    /**
     * TBA
     */
    private void svQueue1Program(int size) {
        int[] arr = new int[size]; // Data domain is {0,1,2}
        for (int i = 0; i < size; i++) {
            arr[i] = i % 3; // Data domain is {0,1,2}
        }

        SVQueue q = new SVQueue(size);
        SharedState sharedState = new SharedState(size);
        ReentrantLock lock = new ReentrantLock();

        Producer3 producer = new Producer3(q, lock, size, sharedState, arr);
        Consumer3 consumer = new Consumer3(q, lock, size, sharedState);

        producer.start();
        consumer.start();

        try {
            producer.join();
            consumer.join();
        } catch (InterruptedException e) {

        }
    }

    /**
     * TBA
     */
    private void svQueue2Program(int size) {
        int[] arr = new int[size]; // Data domain is {0,1,2}
        for (int i = 0; i < size; i++) {
            arr[i] = i % 3; // Data domain is {0,1,2}
        }

        SVQueue q = new SVQueue(size);
        SharedState sharedState = new SharedState(size);
        ReentrantLock lock = new ReentrantLock();

        Producer2 producer = new Producer2(q, lock, size, sharedState, arr);
        Consumer2 consumer = new Consumer2(q, lock, size, sharedState);

        producer.start();
        consumer.start();

        try {
            producer.join();
            consumer.join();
        } catch (InterruptedException e) {

        }
    }

    /**
     * TBA
     */
    private void svQueue3Program(int size) {
        int[] arr = new int[size]; // Data domain is {0,1,2}
        for (int i = 0; i < size; i++) {
            arr[i] = i % 3; // Data domain is {0,1,2}
        }

        SVQueue q = new SVQueue(size);
        SharedState sharedState = new SharedState(size);
        ReentrantLock lock = new ReentrantLock();

        Producer producer = new Producer(q, lock, size, sharedState, arr);
        Consumer consumer = new Consumer(q, lock, size, sharedState);

        producer.start();
        consumer.start();

        try {
            producer.join();
            consumer.join();
        } catch (InterruptedException e) {

        }
    }

    /**
     * TBA
     */
    private void svStack1Program(int size) {
        Integer[] arr = new Integer[size];
        for (int i = 0; i < size; i++) {
            arr[i] = i % 3; // data domain {0,1,2}
        }

        SVStack stack = new SVStack(size);
        ReentrantLock lock = new ReentrantLock();
        org.mpi_sws.jmc.test.det.stack.svStack.Producer producer = new org.mpi_sws.jmc.test.det.stack.svStack.Producer(stack, size, lock, arr);
        org.mpi_sws.jmc.test.det.stack.svStack.Consumer consumer = new org.mpi_sws.jmc.test.det.stack.svStack.Consumer(stack, size, lock);
        producer.start();
        consumer.start();
        try {
            producer.join();
            consumer.join();
        } catch (InterruptedException e) {

        }
    }

    /**
     * TBA
     */
    private void svStack2Program(int size) {
        Integer[] arr = new Integer[size];
        for (int i = 0; i < size; i++) {
            arr[i] = i % 3; // data domain {0,1,2}
        }

        SVStack stack = new SVStack(size);
        ReentrantLock lock = new ReentrantLock();
        org.mpi_sws.jmc.test.det.stack.svStack.Shared shared = new org.mpi_sws.jmc.test.det.stack.svStack.Shared();
        org.mpi_sws.jmc.test.det.stack.svStack.Producer2 producer = new org.mpi_sws.jmc.test.det.stack.svStack.Producer2(stack, size, lock, shared, arr);
        org.mpi_sws.jmc.test.det.stack.svStack.Consumer2 consumer = new org.mpi_sws.jmc.test.det.stack.svStack.Consumer2(stack, size, lock, shared);
        producer.start();
        consumer.start();
        try {
            producer.join();
            consumer.join();
        } catch (InterruptedException e) {

        }
    }

    /**
     * TBA
     */
    private void coarseCounter(int size) {
        CCounter counter = new CCounter();
        int NUM_INSERTIONS = (int) Math.ceil(size / 2.0);
        int NUM_DELETIONS = (int) Math.floor(size / 2.0);

        int i;
        IncThread[] threads = new IncThread[NUM_INSERTIONS];
        for (i = 0; i < NUM_INSERTIONS; i++) {
            int arg = i;
            threads[i] = new IncThread(counter, arg);
        }

        DecThread[] threads2 = new DecThread[NUM_DELETIONS];
        for (i = NUM_INSERTIONS; i < size; i++) {
            int arg = i;
            threads2[i - NUM_INSERTIONS] = new DecThread(counter, arg);
        }

        for (i = 0; i < NUM_INSERTIONS; i++) {
            threads[i].start();
        }

        for (i = 0; i < NUM_DELETIONS; i++) {
            threads2[i].start();
        }

        for (i = 0; i < NUM_INSERTIONS; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
            }
        }

        for (i = 0; i < NUM_DELETIONS; i++) {
            try {
                threads2[i].join();
            } catch (InterruptedException e) {
            }
        }
    }

    /**
     * TBA
     */
    private void fineCounterProgram(int size) {
        FCounter counter = new FCounter();
        int NUM_INSERTIONS = (int) Math.ceil(size / 2.0);
        int NUM_DELETIONS = (int) Math.floor(size / 2.0);

        int i;
        IncThread[] threads = new IncThread[NUM_INSERTIONS];
        for (i = 0; i < NUM_INSERTIONS; i++) {
            threads[i] = new IncThread(counter, i);
        }

        DecThread[] threads2 = new DecThread[NUM_DELETIONS];
        for (i = NUM_INSERTIONS; i < size; i++) {
            threads2[i - NUM_INSERTIONS] = new DecThread(counter, i);
        }

        for (i = 0; i < NUM_INSERTIONS; i++) {
            threads[i].start();
        }

        for (i = 0; i < NUM_DELETIONS; i++) {
            threads2[i].start();
        }

        for (i = 0; i < NUM_INSERTIONS; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
            }
        }

        for (i = 0; i < NUM_DELETIONS; i++) {
            try {
                threads2[i].join();
            } catch (InterruptedException e) {
            }
        }
    }

    /**
     * TBA
     */
    private void bigShotP() {
        Str s = new Str();
        T1 t1 = new T1(s);
        T2 t2 = new T2(s);

        t1.start();
        t2.start();

        try {
            t1.join();
            t2.join();

            assert s.v == "" || s.v.charAt(0) == 'b' : "Assertion Fail! ";
        } catch (InterruptedException e) {

        }
    }

    /**
     * TBA
     */
    private void bigShotS() {
        Str s = new Str();
        T1 t1 = new T1(s);
        T2 t2 = new T2(s);

        try {
            t1.start();
            t1.join();

            t2.start();
            t2.join();

            assert Objects.equals(s.v, "") || s.v.charAt(0) == 'b' : "Assertion Fail! ";
        } catch (InterruptedException e) {

        }
    }

    /**
     * TBA
     */
    private void fib1Program(int size) {
        ReentrantLock lock = new ReentrantLock();
        FibShared shared = new FibShared();
        FibThread1 t1 = new FibThread1(shared, size, lock);
        FibThread2 t2 = new FibThread2(shared, size, lock);

        t1.start();
        t2.start();

        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {

        }

        //boolean condI = shared.i > 144;
        //boolean condJ = shared.j > 144;

        //assert !(condI || condJ) : "Assertion Fail! ";
    }

    /**
     * TBA
     */
    private void sigmaProgram(int SIZE) {
        SigmaShared shared = new SigmaShared(SIZE);
        ArrayList<SigmaThread> threads = new ArrayList<>(SIZE);

        for (int i = 0; i < SIZE; i++) {
            SigmaThread thread = new SigmaThread(shared);
            threads.add(thread);
        }

        for (int i = 0; i < SIZE; i++) {
            threads.get(i).start();
        }

        for (int i = 0; i < SIZE; i++) {
            try {
                threads.get(i).join();
            } catch (InterruptedException e) {

            }
        }
        int sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += shared.array[i];
        }
    }

    /**
     * TBA
     */
    private void singleToneWUPProgram() {
        SingletoneShared shared = new SingletoneShared();
        ReentrantLock lock = new ReentrantLock();
        SingletoneThread0 t0 = new SingletoneThread0(shared, lock);

        t0.start();
        try {
            t0.join();
        } catch (InterruptedException e) {

        }
        assert (shared.c == 'X' || shared.c == 'Y') : "shared.c != X";

    }

    /** ---------------------------------------------------------------------------------------------------------
     *  Test suites for the synthetic programs. Each test suite includes 2 tests: one for running the TruSt
     *  model checking, and the other for running the Testor strategy. Note that in the TruSt model checking test,
     *  make sure the {@JmcTrustStrategy} annotation has the loggerTree set to true, so that we can get the tree of
     *  Trust. In Testor tests, make sure to set the strategy to "testor" and the scheduling policy to FIFO, or LIFO,
     *  to make the scheduler deterministic. This is important for fixing the structure of the tree of Trust.
     *  In order to set the budget for Testor, we can use the budget parameter in the {@JmcCheckConfiguration}
     *  annotation. Also, you can modify the size of tests (e.g., the number of threads) by changing the input
     *  parameters of the program methods (e.g., readNProgram, incNProgram, etc.).
     *  ----------------------------------------------------------------------------------------------------------*/

    /**
     * R(n) test suite for n \in {2,3,4,5}
     * 1. TruSt model checking
     * 2. Testor strategy
     */

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy(loggerTree = true)
    @JmcExpectExecutions(1) // For any n is 1
    public void runRnTrust() {
        readNProgram(3);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000,
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, strategy = "testor",
            budget = 10, timeout = 300000L)
    public void runRnTestor() {
        readNProgram(3);
    }

    /** ----------------------------------------------------*/

    /**
     * Inc(n) test suite for n \in {2,3,4,5}
     * 1. TruSt model checking
     * 2. Testor strategy
     */

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 2000000)
    @JmcTrustStrategy(loggerTree = true, schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, debug = false)
    @JmcExpectExecutions(576) // For input n is (n!)^2
    public void runIncnTrust() {
        incNProgram(4);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000,
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, strategy = "testor",
            budget = 10, timeout = 300000L)
    public void runIncnTestor() {
        incNProgram(3);
    }

    /** ----------------------------------------------------*/

    /**
     * RW(n) test suite for n \in {2,3,4,5}
     * 1. TruSt model checking
     * 2. Testor strategy
     */

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 2000000)
    @JmcTrustStrategy(loggerTree = true, schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO)
    public void runRWNTrust() {
        RWNProgram(2, 2);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000,
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, strategy = "testor",
            budget = 10, timeout = 300000L)
    public void runRWNTestor() {
        RWNProgram(2, 2);
    }

    /** ----------------------------------------------------*/

    /**
     * WR(n) test suite for n \in {2,3,4,5}
     * 1. TruSt model checking
     * 2. Testor strategy
     */

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 2000000)
    @JmcTrustStrategy(loggerTree = true, schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO)
    public void runWRNTrust() {
        WRNProgram(2, 2);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 5000, schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, strategy = "testor", debug = false, budget = 4)
    public void runWRNTestor() {
        WRNProgram(2, 2);
    }

    /** ----------------------------------------------------*/

    /**
     * SVQueue(1) test suite
     * 1. TruSt model checking
     * 2. Testor strategy
     */

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy(loggerTree = true, schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO)
    public void runSVQueue1Trust() {
        svQueue1Program(3);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 5000, schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, strategy = "testor", debug = false, budget = 4)
    public void runSVQueue1Testor() {
        svQueue1Program(3);
    }

    /** ----------------------------------------------------*/

    /**
     * SVQueue(2) test suite
     * 1. TruSt model checking
     * 2. Testor strategy
     */

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000)
    @JmcTrustStrategy(loggerTree = true, schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO)
    public void runSVQueue2Trust() {
        svQueue2Program(3);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 5000, schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, strategy = "testor", debug = false, budget = 4)
    public void runSVQueue2Testor() {
        svQueue2Program(3);
    }

    /** ----------------------------------------------------*/

    /**
     * SVQueue(3) test suite
     * 1. TruSt model checking
     * 2. Testor strategy
     */

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000)
    @JmcTrustStrategy(loggerTree = true, schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO)
    public void runSVQueue3Trust() {
        svQueue3Program(3);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 5000, schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, strategy = "testor", debug = false, budget = 4)
    public void runSVQueue3Testor() {
        svQueue3Program(3);
    }

    /** ----------------------------------------------------*/

    /**
     * SVStack1 test suite for size in {2,3,4,5,6}
     * 1. TruSt model checking
     * 2. Testor strategy
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runSVStack1Trust() {
        svStack1Program(3);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 5000, schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, strategy = "testor", debug = false, budget = 4)
    public void runSVStack1Testor() {
        svStack1Program(3);
    }

    /** ----------------------------------------------------*/

    /**
     * SVStack2 test suite for size in {2,3,4,5,6}
     * 1. TruSt model checking
     * 2. Testor strategy
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runSVStack2Trust() {
        svStack2Program(3);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 5000, schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, strategy = "testor", debug = false, budget = 4)
    public void runSVStack2Testor() {
        svStack2Program(3);
    }

    /** ----------------------------------------------------*/

    /**
     * Coarse Counter test suite for size in {2,3,4,5,6}
     * 1. TruSt model checking
     * 2. Testor strategy
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runCoarseCounterTrust() {
        coarseCounter(3);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 5000, schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, strategy = "testor", debug = false, budget = 4)
    public void runCoarseCounterTestor() {
        coarseCounter(3);
    }

    /** ----------------------------------------------------*/

    /**
     * Fine Counter test suite for size in {2,3,4,5,6}
     * 1. TruSt model checking
     * 2. Testor strategy
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runFineCounterTrust() {
        fineCounterProgram(4);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 5000, schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, strategy = "testor", debug = false, budget = 4)
    public void runFineCounterTestor() {
        fineCounterProgram(4);
    }

    /** ----------------------------------------------------*/

    /**
     * BigShotP test suite
     * 1. TruSt model checking
     * 2. Testor strategy
     */

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    @JmcExpectExecutions(3)
    public void runBigShotPTest() {
        bigShotP();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 5000, schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, strategy = "testor", debug = false, budget = 4)
    public void runBigShotPTestor() {
        bigShotP();
    }

    /** ----------------------------------------------------*/

    /**
     * BigShotS test suite
     * 1. TruSt Model checking
     * 2. Testor strategy
     */

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    @JmcExpectExecutions(1)
    public void runBigShotSTest() {
        bigShotS();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000,
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, strategy = "testor",
            budget = 10, timeout = 300000L)
    public void runBigShotSTestor() {
        bigShotS();
    }

    /** ----------------------------------------------------*/

    /**
     * FibBench1 test suite
     * 1. TruSt model checking
     * 2. Testor strategy
     */

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runFib1Trust() {
        fib1Program(4);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000,
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, strategy = "testor",
            budget = 10, timeout = 300000L)
    public void runFib1Testor() {
        fib1Program(4);
    }

    /** ----------------------------------------------------*/

    /**
     * Sigma test suite
     * 1. TruSt model checking
     * 2. Testor strategy
     */

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 5000000)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runSigmaTrust() {
        sigmaProgram(3);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 5000, schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, strategy = "testor", debug = false, budget = 4)
    public void runSigmaTestor() {
        sigmaProgram(3);
    }

    /** ----------------------------------------------------*/

    /**
     * Singletone WUP test suite
     * 1. TruSt model checking
     * 2. Testor strategy
     */

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runSingletoneWUPTrust() {
        singleToneWUPProgram();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 5000, schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, strategy = "testor", debug = false, budget = 4)
    @Disabled
    public void runSingletoneWUPTestor() {
        singleToneWUPProgram();
    }

    /** ---------------------------------------------------------------------------------------------------------
     *  Deprecated Test suites for the synthetic programs. Each test suite includes 4 tests: 1. DAG-based estimation,
     *  2. Fork-Join DAG-based estimation, 3. TruSt-based estimation (budget = 1), and 4. Weighted TruSt-based estimation
     *  (budget = 1).
     *  ----------------------------------------------------------------------------------------------------------*/


    /**
     * R(n) test suite for n \in {2,3,4,5}
     * 1. Dag-based estimation
     * 2. Fork-Join DAG-based estimation
     * 3. TruSt-based estimation (budget = 1)
     * 4. Weighted TruSt-based estimation (budget = 1)
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100, strategy = "dag-estimation", debug = false)
    public void runRnDagEstimation() {
        readNProgram(3);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100, strategy = "fj-dag-estimation", debug = false)
    public void runRnFjDagEstimation() {
        readNProgram(3);
    }

    // The scheduling policy can be either FIFO or LIFO, both work fine.
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100, strategy = "trust-estimation",
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, debug = false)
    public void runRnTrustEstimation() {
        readNProgram(3);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100, strategy = "wg-trust-estimation",
            schedulingPolicy = TrustStrategy.SchedulingPolicy.LIFO, debug = false)
    public void runRnWgTrustEstimation() {
        readNProgram(3);
    }

    /** ----------------------------------------------------*/

    /**
     * Inc(n) test suite for n \in {2,3,4,5}
     * 1. DAG-based estimation
     * 2. Fork-Join DAG-based estimation
     * 3. TruSt-based estimation (budget = 1)
     * 4. Weighted TruSt-based estimation (budget = 1)
     */

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100, strategy = "dag-estimation", debug = false)
    public void runIncnDagEstimation() {
        incNProgram(3);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100, strategy = "fj-dag-estimation", debug = false)
    public void runIncnFjDagEstimation() {
        incNProgram(3);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100, strategy = "trust-estimation", debug = false, schedulingPolicy = TrustStrategy.SchedulingPolicy.LIFO)
    public void runIncnTrustEstimation() {
        incNProgram(3);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100, strategy = "wg-trust-estimation", debug = false, schedulingPolicy = TrustStrategy.SchedulingPolicy.LIFO)
    public void runIncnWgTrustEstimation() {
        incNProgram(3);
    }

    /** ----------------------------------------------------*/

    /**
     * RW(n) test suite for n \in {2,3,4,5}
     * 1. DAG-based estimation
     * 2. Fork-Join DAG-based estimation
     * 3. TruSt-based estimation (budget = 1)
     * 4. Weighted TruSt-based estimation (budget = 1)
     */

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100, strategy = "dag-estimation", debug = false)
    public void runRWNnDagEstimation() {
        RWNProgram(2, 2);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100, strategy = "fj-dag-estimation", debug = false)
    public void runRWNnFjDagEstimation() {
        RWNProgram(2, 2);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100, strategy = "trust-estimation", debug = false, schedulingPolicy = TrustStrategy.SchedulingPolicy.LIFO)
    public void runRWNnTrustEstimation() {
        RWNProgram(2, 2);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100, strategy = "wg-trust-estimation", debug = false, schedulingPolicy = TrustStrategy.SchedulingPolicy.LIFO)
    public void runRWNnWgTrustEstimation() {
        RWNProgram(2, 2);
    }

    /** ----------------------------------------------------*/

    /**
     * WR(n) test suite for n \in {2,3,4,5}
     * 1. DAG-based estimation
     * 2. Fork-Join DAG-based estimation
     * 3. TruSt-based estimation (budget = 1)
     * 4. Weighted TruSt-based estimation (budget = 1)
     */

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100, strategy = "dag-estimation", debug = false)
    public void runWRNnDagEstimation() {
        WRNProgram(2, 2);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100, strategy = "fj-dag-estimation", debug = false)
    public void runWRNnFjDagEstimation() {
        WRNProgram(2, 2);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100, strategy = "trust-estimation", debug = false, schedulingPolicy = TrustStrategy.SchedulingPolicy.LIFO)
    public void runWRNnTrustEstimation() {
        WRNProgram(2, 2);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100, strategy = "wg-trust-estimation", debug = false, schedulingPolicy = TrustStrategy.SchedulingPolicy.LIFO)
    public void runWRNnWgTrustEstimation() {
        WRNProgram(2, 2);
    }

    /** ----------------------------------------------------*/
}
