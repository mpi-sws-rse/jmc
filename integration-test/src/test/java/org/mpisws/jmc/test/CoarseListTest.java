package org.mpisws.jmc.test;

import org.mpisws.jmc.annotations.JmcExpectExecutions;
import org.mpisws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpisws.jmc.strategies.trust.TrustStrategy;
import org.mpisws.jmc.test.list.DeletionThread;
import org.mpisws.jmc.test.list.InsertionThread;
import org.mpisws.jmc.test.list.Set;
import org.mpisws.jmc.test.list.coarse.CoarseList;
import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;

import java.util.ArrayList;
import java.util.List;

public class CoarseListTest {
    private void test_50_50_workload_coarse_list(int NUM_THREADS) {

        int NUM_INSERTIONS = (int) Math.ceil(NUM_THREADS / 2.0);
        int NUM_DELETIONS = (int) Math.floor(NUM_THREADS / 2.0);

        int[] arr = new int[NUM_INSERTIONS];
        for (int i = 0; i < NUM_INSERTIONS; i++) {
            arr[i] = i + 1; // Fixing input data
        }

        Set set = new CoarseList();

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
        test_50_50_workload_coarse_list(6);
    }

    // Running with JMC using the trust strategy.
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 20000)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO)
    @JmcExpectExecutions(120)
    public void runTrustCoarseListTest() {
        test_50_50_workload_coarse_list(5);
    }
}
