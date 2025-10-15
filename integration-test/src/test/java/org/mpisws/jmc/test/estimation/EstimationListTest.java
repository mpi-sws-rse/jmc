package org.mpisws.jmc.test.estimation;

import org.mpisws.jmc.annotations.JmcExpectExecutions;
import org.mpisws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpisws.jmc.strategies.trust.TrustStrategy;
import org.mpisws.jmc.test.det.list.DeletionThread;
import org.mpisws.jmc.test.det.list.InsertionThread;
import org.mpisws.jmc.test.det.list.Set;
import org.mpisws.jmc.test.det.list.coarse.CoarseList;
import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.test.det.list.fine.FineList;
import org.mpisws.jmc.test.det.list.lazy.LazyList;
import org.mpisws.jmc.test.det.list.opt.OptList;

public class EstimationListTest {

    /**
     * CoarseListI(n): This program starts n insertion threads, where each thread inserts a unique item into
     * a shared coarse list. This program has n! distinct execution graphs.
     * The abstract model of this program is like this:
     * Main thread  |  T1       |  T2       |  T3
     * TBA
     */
    private void coarseListIProgram(int numThreads) {
        list_100_test(numThreads, new CoarseList());
    }

    /**
     * CoarseListID(n): This program starts n/2 insertion threads and n/2 deletion threads, where each insertion thread
     * inserts a unique item into a shared coarse list, and each deletion thread deletes a unique item from the shared
     * list. This program has n! distinct execution graphs.
     * The abstract model of this program is like this:
     * Main thread  |  T1        |  T2        |  T3      |  T4
     * TBA
     */
    private void coarseListIDProgram(int NUM_THREADS) {
        list_50_50_test(NUM_THREADS, new CoarseList());
    }

    /**
     * FineListI(n): This program starts n insertion threads, where each thread inserts a unique item into
     * a shared fine list. This program has n! distinct execution graphs.
     * The abstract model of this program is like this:
     * Main thread  |  T1       |  T2       |  T3
     * TBA
     */
    private void fineListIProgram(int numThreads) {
        list_100_test(numThreads, new FineList());
    }

    /**
     * FineListID(n): This program starts n/2 insertion threads and n/2 deletion threads, where each insertion thread
     * inserts a unique item into a shared fine list, and each deletion thread deletes a unique item from the shared
     * list. This program has n! distinct execution graphs.
     * The abstract model of this program is like this:
     * Main thread  |  T1        |  T2        |  T3      |  T4
     * TBA
     */
    private void fineListIDProgram(int NUM_THREADS) {
        list_50_50_test(NUM_THREADS, new FineList());
    }

    /**
     * OptListI(n): This program starts n insertion threads, where each thread inserts a unique item into
     * a shared opt list. This program has 4 distinct execution graphs for n = 2, and 67 distinct execution graphs
     * for n = 3. The abstract model of this program is like this:
     * Main thread  |  T1       |  T2       |  T3
     * TBA
     */
    private void optListIProgram(int numThreads) {
        list_100_test(numThreads, new OptList());
    }

    /**
     * OptListID(n): This program starts n/2 insertion threads and n/2 deletion threads, where each insertion thread
     * inserts a unique item into a shared opt list, and each deletion thread deletes a unique item from the shared
     * list. This program has 3 distinct execution graphs for n = 2, and 42 distinct execution graphs for n = 3.
     * The abstract model of this program is like this:
     * Main thread  |  T1        |  T2        |  T3      |  T4
     * TBA
     */
    private void optListIDProgram(int NUM_THREADS) {
        list_50_50_test(NUM_THREADS, new OptList());
    }

    /**
     * LazyListI(n): This program starts n insertion threads, where each thread inserts a unique item into
     * a shared lazy list. This program has 4 distinct execution graphs for n = 2, and 67 distinct execution graphs
     * for n = 3. The abstract model of this program is like this:
     * Main thread  |  T1       |  T2       |  T3
     * TBA
     */
    private void lazyListIProgram(int numThreads) {
        list_100_test(numThreads, new LazyList());
    }

    /**
     * LazyListID(n): This program starts n/2 insertion threads and n/2 deletion threads, where each insertion thread
     * inserts a unique item into a shared lazy list, and each deletion thread deletes a unique item from the shared
     * list. This program has 3 distinct execution graphs for n = 2, and 42 distinct execution graphs for n = 3.
     * The abstract model of this program is like this:
     * Main thread  |  T1        |  T2        |  T3      |  T4
     * TBA
     */
    private void lazyListIDProgram(int NUM_THREADS) {
        list_50_50_test(NUM_THREADS, new LazyList());
    }

    /** ----------------------------------------------------
     * Helper methods to run the tests
     * ----------------------------------------------------*/

    /**
     * This method runs NUM_THREADS insertion threads on the given set.
     * Each thread inserts a unique item from 1 to NUM_THREADS.
     */
    private void list_100_test(int NUM_THREADS, Set set) {
        int NUM_INSERTIONS = NUM_THREADS;

        int[] arr = new int[NUM_INSERTIONS];
        for (int i = 0; i < NUM_INSERTIONS; i++) {
            arr[i] = i + 1; // Fixing input data
        }

        InsertionThread[] insertionThreads = new InsertionThread[NUM_INSERTIONS];
        for (int i = 0; i < NUM_INSERTIONS; i++) {
            int item = arr[i];
            InsertionThread ithread = new InsertionThread(set, item);
            insertionThreads[i] = ithread;
        }

        for (int i = 0; i < NUM_INSERTIONS; i++) {
            insertionThreads[i].start();
        }

        for (int i = 0; i < NUM_INSERTIONS; i++) {
            try {
                insertionThreads[i].join();
            } catch (InterruptedException e) {

            }
        }
    }

    /**
     * This method runs NUM_THREADS/2 insertion threads and NUM_THREADS/2 deletion threads on the given set.
     * Each insertion thread inserts a unique item from 1 to NUM_THREADS/2.
     * Each deletion thread deletes a unique item from 1 to NUM_THREADS/2.
     * If NUM_THREADS is odd, then there will be one more insertion thread than deletion threads
     */
    private void list_50_50_test(int NUM_THREADS, Set set) {
        int NUM_INSERTIONS = (int) Math.ceil(NUM_THREADS / 2.0);
        int NUM_DELETIONS = (int) Math.floor(NUM_THREADS / 2.0);

        int[] arr = new int[NUM_INSERTIONS];
        for (int i = 0; i < NUM_INSERTIONS; i++) {
            arr[i] = i + 1; // Fixing input data
        }

        InsertionThread[] insertionThreads = new InsertionThread[NUM_INSERTIONS];
        for (int i = 0; i < NUM_INSERTIONS; i++) {
            int item = arr[i];
            InsertionThread ithread = new InsertionThread(set, item);
            insertionThreads[i] = ithread;
        }

        DeletionThread[] deleteThreads = new DeletionThread[NUM_DELETIONS];
        for (int i = 0; i < NUM_DELETIONS; i++) {
            int item = arr[i];
            DeletionThread dthread = new DeletionThread(set, item);
            deleteThreads[i] = dthread;
        }

        for (int i = 0; i < NUM_INSERTIONS; i++) {
            insertionThreads[i].start();
        }

        for (int i = 0; i < NUM_DELETIONS; i++) {
            deleteThreads[i].start();
        }

        for (int i = 0; i < NUM_INSERTIONS; i++) {
            try {
                insertionThreads[i].join();
            } catch (InterruptedException e) {

            }
        }

        for (int i = 0; i < NUM_DELETIONS; i++) {
            try {
                deleteThreads[i].join();
            } catch (InterruptedException e) {

            }
        }
    }

    /**
     * ----------------------------------------------------
     * Test suite
     * ----------------------------------------------------
     */


    /**
     * CoarseListI(n) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     * 2. TruSt-based estimation
     * 3. Weighted TruSt-based estimation (Wg-TruSt)
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO)
    @JmcExpectExecutions(720) // For any n is n!
    public void runCoarseListITrust() {
        coarseListIProgram(6);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100, strategy = "trust-estimation",
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, debug = false)
    public void runCoarseListITrustEstimation() {
        coarseListIProgram(4);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 200, strategy = "wg-trust-estimation",
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, debug = false)
    public void runCoarseListIWgTrustEstimation() {
        coarseListIProgram(4);
    }

    /**
     * CoarseListID(n) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     * 2. TruSt-based estimation
     * 3. Weighted TruSt-based estimation (Wg-TruSt)
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy
    @JmcExpectExecutions(24) // For any n is n!
    public void runCoarseListIDTrust() {
        coarseListIDProgram(4);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10000, strategy = "trust-estimation",
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, debug = false)
    public void runCoarseListIDTrustEstimation() {
        coarseListIDProgram(4);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 200, strategy = "wg-trust-estimation",
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, debug = false)
    public void runCoarseListIDWgTrustEstimation() {
        coarseListIDProgram(4);
    }

    /**
     * FineListI(n) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     * 2. TruSt-based estimation
     * 3. Weighted TruSt-based estimation (Wg-TruSt)
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy
    @JmcExpectExecutions(720) // For any n is n!
    public void runFineListITrust() {
        fineListIProgram(6);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10000, strategy = "trust-estimation",
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, debug = false)
    public void runFineListITrustEstimation() {
        fineListIProgram(4);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 200, strategy = "wg-trust-estimation",
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, debug = false)
    public void runFineListIWgTrustEstimation() {
        fineListIProgram(4);
    }

    /**
     * FineListID(n) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     * 2. TruSt-based estimation
     * 3. Weighted TruSt-based estimation (Wg-TruSt)
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy
    @JmcExpectExecutions(720) // For any n is n!
    public void runFineListIDTrust() {
        fineListIDProgram(6);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10000, strategy = "trust-estimation",
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, debug = false)
    public void runFineListIDTrustEstimation() {
        fineListIDProgram(4);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 200, strategy = "wg-trust-estimation",
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, debug = false)
    public void runFineListIDWgTrustEstimation() {
        fineListIDProgram(4);
    }

    /**
     * OptListI(n) test suite for n \in {2,3}
     * 1. TruSt model checking
     * 2. TruSt-based estimation
     * 3. Weighted TruSt-based estimation (Wg-TruSt)
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy
    @JmcExpectExecutions(4532) // For n = 2 is 4, for n = 3 is 67, for n = 4 is 4532
    public void runOptListITrust() {
        optListIProgram(4);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10000, strategy = "trust-estimation",
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, debug = false)
    public void runOptListITrustEstimation() {
        optListIProgram(3);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 200, strategy = "wg-trust-estimation",
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, debug = false)
    public void runOptListIWgTrustEstimation() {
        optListIProgram(3);
    }

    /**
     * OptListID(n) test suite for n \in {2,3}
     * 1. TruSt model checking
     * 2. TruSt-based estimation
     * 3. Weighted TruSt-based estimation (Wg-TruSt)
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy
    @JmcExpectExecutions(1687) // For n = 2, is 3, n = 3, is 42, n = 4 is 1687
    public void runOptListIDTrust() {
        optListIDProgram(4);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10000, strategy = "trust-estimation",
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, debug = false)
    public void runOptListIDTrustEstimation() {
        optListIDProgram(2);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 200, strategy = "wg-trust-estimation",
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, debug = false)
    public void runOptListIDWgTrustEstimation() {
        optListIDProgram(2);
    }

    /**
     * LazyListI(n) test suite for n \in {2,3}
     * 1. TruSt model checking
     * 2. TruSt-based estimation
     * 3. Weighted TruSt-based estimation (Wg-TruSt)
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO)
    @JmcExpectExecutions(4383) // For n = 2, is 4 and for n = 3, is 67 and for n = 4 is 4383
    public void runLazyListITrust() {
        lazyListIProgram(4);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10000, strategy = "trust-estimation",
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, debug = false)
    public void runLazyListITrustEstimation() {
        lazyListIProgram(2);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 200, strategy = "wg-trust-estimation",
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, debug = false)
    public void runLazyListIWgTrustEstimation() {
        lazyListIProgram(2);
    }

    /**
     * LazyListID(n) test suite for n \in {2,3}
     * 1. TruSt model checking
     * 2. TruSt-based estimation
     * 3. Weighted TruSt-based estimation (Wg-TruSt)
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy
    @JmcExpectExecutions(1687) // For n = 2, is 3, n = 3, is 42 and for n = 4 is 1687
    public void runLazyListIDTrust() {
        lazyListIDProgram(4);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10000, strategy = "trust-estimation",
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, debug = false)
    public void runLazyListIDTrustEstimation() {
        lazyListIDProgram(2);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 200, strategy = "wg-trust-estimation",
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, debug = false)
    public void runLazyListIDWgTrustEstimation() {
        lazyListIDProgram(2);
    }
}
