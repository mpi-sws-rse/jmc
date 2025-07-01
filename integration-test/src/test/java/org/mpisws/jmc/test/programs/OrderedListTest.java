package org.mpisws.jmc.test.programs;

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

public class OrderedListTest {

    private void test_100_0_workload_coarse_list(int NUM_THREADS) {

        int NUM_INSERTIONS = NUM_THREADS;

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

    private void test_100_0_workload_fine_list(int NUM_THREADS) {
        int NUM_INSERTIONS = NUM_THREADS;

        int[] arr = new int[NUM_INSERTIONS];
        for (int i = 0; i < NUM_INSERTIONS; i++) {
            arr[i] = i + 1; // Fixing input data
        }

        Set set = new FineList();

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

    private void test_50_50_workload_fine_list(int NUM_THREADS) {
        int NUM_INSERTIONS = (int) Math.ceil(NUM_THREADS / 2.0);
        int NUM_DELETIONS = (int) Math.floor(NUM_THREADS / 2.0);

        int[] arr = new int[NUM_INSERTIONS];
        for (int i = 0; i < NUM_INSERTIONS; i++) {
            arr[i] = i + 1; // Fixing input data
        }

        Set set = new FineList();

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

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100)
    public void runRandom_100_0_CoarseListTest() {
        test_100_0_workload_coarse_list(7);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy()
    @JmcExpectExecutions(5040) // For input 7
    public void runTrust_100_0_CoarseListTest() {
        test_100_0_workload_coarse_list(7);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000)
    public void runRandom_50_50_CoarseListTest() {
        test_50_50_workload_coarse_list(6);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy()
    @JmcExpectExecutions(5040) // For input 7
    public void runTrust_50_50_CoarseListTest() {
        test_50_50_workload_coarse_list(7);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000)
    public void runRandom_100_0_FineListTest() {
        test_100_0_workload_fine_list(7);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy()
    @JmcExpectExecutions(5040) // For input 7
    public void runTrust_100_0_FineListTest() {
        test_100_0_workload_fine_list(7);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000)
    public void runRandom_50_50_FineListTest() {
        test_50_50_workload_fine_list(6);
    }


    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy()
    // TODO :: Fix this test
    public void runTrust_50_50_FineListTest() {
        test_50_50_workload_fine_list(7);
    }
}
