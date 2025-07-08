package org.mpisws.jmc.test;

import org.mpisws.jmc.annotations.JmcExpectExecutions;
import org.mpisws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpisws.jmc.strategies.trust.TrustStrategy;
import org.mpisws.jmc.test.det.list.DeletionThread;
import org.mpisws.jmc.test.det.list.InsertionThread;
import org.mpisws.jmc.test.det.list.Set;
import org.mpisws.jmc.test.det.list.coarse.CoarseList;
import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.test.det.list.lazy.LazyList;

import java.util.ArrayList;
import java.util.List;

public class OrderedListTest {
    private void coarseList_50_50_test(int NUM_THREADS) {
        list_50_50_test(NUM_THREADS, new CoarseList());
    }

    private void lazyList_50_50_test(int NUM_THREADS) {
        list_50_50_test(NUM_THREADS, new LazyList());
    }

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

    // Running with JMC using the default configuration. (random
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100)
    public void runRandomCoarseListTest() {
        coarseList_50_50_test(6);
    }

    // Running with JMC using the trust strategy.
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 20000)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO)
    @JmcExpectExecutions(120)
    public void runTrustCoarseListTest() {
        coarseList_50_50_test(5);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10000, strategy = "estimation")
    public void runEstimationCoarseList() {
        coarseList_50_50_test(6);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10000, strategy = "estimation")
    public void runEstimationLazyList() {
        lazyList_50_50_test(6);
    }
}
