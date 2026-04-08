package org.mpi_sws.jmc.test.estimation;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpi_sws.jmc.strategies.trust.TrustStrategy;
import org.mpi_sws.jmc.test.det.queue.hwQueue.HWQueue;
import org.mpi_sws.jmc.test.det.queue.Queue;
import org.mpi_sws.jmc.test.det.queue.DeletionThread;
import org.mpi_sws.jmc.test.det.queue.InsertionThread;
import org.mpi_sws.jmc.test.det.queue.lbQueue.LBQueue;
import org.mpi_sws.jmc.test.det.queue.msQueue.MSQueue;
import org.mpi_sws.jmc.test.det.queue.pQueue.PQueue;
import org.mpi_sws.jmc.test.det.queue.pQueue.linear.LockBasedLinear;
import org.mpi_sws.jmc.test.det.queue.ubQueue.UnboundedQueue;

import java.util.ArrayList;
import java.util.List;

public class EstimationQueueTest {

    /**
     * HWQueue(n): This program starts n threads that each enqueue an item onto an initially empty HWQueue.
     *
     * @param NUM_OPERATIONS number of operations
     */
    private void hwQueueEnqueueProgram(int NUM_OPERATIONS) {
        queue_100_test(NUM_OPERATIONS, new HWQueue(NUM_OPERATIONS));
    }

    /**
     * HWQueue(n/2, n/2): This program starts n/2 threads that each enqueue an item onto an initially empty HWQueue,
     * and n/2 threads that each dequeue an item from the queue.
     *
     * @param NUM_OPERATIONS number of operations
     */
    private void hwQueueEnqueueDequeueProgram(int NUM_OPERATIONS) {
        queue_50_50_test(NUM_OPERATIONS, new HWQueue((int) Math.ceil(NUM_OPERATIONS / 2.0)));
    }

    /**
     * LBQueue(n): This program starts n threads that each enqueue an item onto an initially empty LBQueue.
     *
     * @param NUM_OPERATIONS number of operations
     */
    private void lbQueueEnqueueProgram(int NUM_OPERATIONS) {
        queue_100_test(NUM_OPERATIONS, new LBQueue(NUM_OPERATIONS));
    }

    /**
     * LBQueue(n/2, n/2): This program starts n/2 threads that each enqueue an item onto an initially empty LBQueue,
     * and n/2 threads that each dequeue an item from the queue.
     *
     * @param NUM_OPERATIONS number of operations
     */
    private void lbQueueEnqueueDequeueProgram(int NUM_OPERATIONS) {
        queue_50_50_test(NUM_OPERATIONS, new LBQueue((int) Math.ceil(NUM_OPERATIONS / 2.0)));
    }

    /**
     * UnboundedQueue(n): This program starts n threads that each enqueue an item onto an initially empty UnboundedQueue.
     *
     * @param NUM_OPERATIONS number of operations
     */
    private void ubQueueEnqueueProgram(int NUM_OPERATIONS) {
        queue_100_test(NUM_OPERATIONS, new UnboundedQueue());
    }

    /**
     * UnboundedQueue(n/2, n/2): This program starts n/2 threads that each enqueue an item onto an initially empty UnboundedQueue,
     * and n/2 threads that each dequeue an item from the queue.
     *
     * @param NUM_OPERATIONS number of operations
     */
    private void ubQueueEnqueueDequeueProgram(int NUM_OPERATIONS) {
        queue_50_50_test(NUM_OPERATIONS, new UnboundedQueue());
    }

    private void lbArrayPQueueEnqueueProgram(int NUM_OPERATIONS) {
        pqueue_100_test(NUM_OPERATIONS, new LockBasedLinear(NUM_OPERATIONS));
    }

    private void lbArrayPQueueEnqueueDequeueProgram(int NUM_OPERATIONS) {
        pqueue_50_50_test(NUM_OPERATIONS, new LockBasedLinear((int) Math.ceil(NUM_OPERATIONS / 2.0)));
    }

    private void msQueueEnqueueProgram(int NUM_OPERATIONS) {
        queue_100_test(NUM_OPERATIONS, new MSQueue());
    }

    private void msQueueEnqueueDequeueProgram(int NUM_OPERATIONS) {
        queue_50_50_test(NUM_OPERATIONS, new MSQueue());
    }

    /**
     * ----------------------------------------------------
     * Helper methods to run the tests
     * ----------------------------------------------------
     */


    private void queue_100_test(int NUM_OPERATIONS, Queue queue) {
        int NUM_ENQUEUE = NUM_OPERATIONS;

        List<Integer> items = new ArrayList<>(NUM_ENQUEUE);
        for (int i = 0; i < NUM_ENQUEUE; i++) {
            items.add(i + 1);
        }

        List<InsertionThread> insertionThreads = new ArrayList<>(NUM_ENQUEUE);
        for (int i = 0; i < NUM_ENQUEUE; i++) {
            Integer item = items.get(i);
            InsertionThread insertionThread = new InsertionThread(queue, item);
            insertionThreads.add(insertionThread);
        }

        for (int i = 0; i < NUM_ENQUEUE; i++) {
            insertionThreads.get(i).start();
        }

        for (int i = 0; i < NUM_ENQUEUE; i++) {
            try {
                insertionThreads.get(i).join();
            } catch (InterruptedException e) {

            }
        }
    }

    private void pqueue_100_test(int NUM_OPERATIONS, PQueue pqueue) {
        int[] arr = new int[NUM_OPERATIONS];
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            arr[i] = i % 3; // Data domain is {0,1,2}
        }

        org.mpi_sws.jmc.test.det.queue.pQueue.InsertionThread[] threads = new
                org.mpi_sws.jmc.test.det.queue.pQueue.InsertionThread[NUM_OPERATIONS];
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            org.mpi_sws.jmc.test.det.queue.pQueue.InsertionThread thread = new
                    org.mpi_sws.jmc.test.det.queue.pQueue.InsertionThread();
            thread.pqueue = pqueue;
            thread.item = i;
            thread.score = arr[i];
            threads[i] = thread;
        }

        for (int i = 0; i < NUM_OPERATIONS; i++) {
            threads[i].start();
        }

        for (int i = 0; i < NUM_OPERATIONS; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {

            }
        }
    }

    private void pqueue_50_50_test(int NUM_OPERATIONS, PQueue pqueue) {
        int NUM_INSERTIONS = (int) Math.ceil(NUM_OPERATIONS / 2.0);
        int NUM_DELETIONS = (int) Math.floor(NUM_OPERATIONS / 2.0);

        int[] arr = new int[NUM_INSERTIONS];
        for (int i = 0; i < NUM_INSERTIONS; i++) {
            arr[i] = i % 3; // Data domain is {0,1,2}
        }

        org.mpi_sws.jmc.test.det.queue.pQueue.InsertionThread[] insertionThreads = new
                org.mpi_sws.jmc.test.det.queue.pQueue.InsertionThread[NUM_INSERTIONS];
        for (int i = 0; i < NUM_INSERTIONS; i++) {
            org.mpi_sws.jmc.test.det.queue.pQueue.InsertionThread thread = new
                    org.mpi_sws.jmc.test.det.queue.pQueue.InsertionThread();
            thread.pqueue = pqueue;
            thread.item = i;
            thread.score = arr[i];
            insertionThreads[i] = thread;
        }

        org.mpi_sws.jmc.test.det.queue.pQueue.DeletionThread[] deletionThreads = new
                org.mpi_sws.jmc.test.det.queue.pQueue.DeletionThread[NUM_DELETIONS];
        for (int i = 0; i < NUM_DELETIONS; i++) {
            org.mpi_sws.jmc.test.det.queue.pQueue.DeletionThread thread = new
                    org.mpi_sws.jmc.test.det.queue.pQueue.DeletionThread();
            thread.pqueue = pqueue;
            thread.item = i;
            deletionThreads[i] = thread;
        }

        for (int i = 0; i < NUM_INSERTIONS; i++) {
            insertionThreads[i].start();
        }

        for (int i = 0; i < NUM_DELETIONS; i++) {
            deletionThreads[i].start();
        }

        for (int i = 0; i < NUM_INSERTIONS; i++) {
            try {
                insertionThreads[i].join();
            } catch (InterruptedException e) {

            }
        }

        for (int i = 0; i < NUM_DELETIONS; i++) {
            try {
                deletionThreads[i].join();
            } catch (InterruptedException e) {

            }
        }
    }

    private void queue_50_50_test(int NUM_OPERATIONS, Queue queue) {
        int NUM_ENQUEUE = (int) Math.ceil(NUM_OPERATIONS / 2.0);
        int NUM_DEQUEUE = (int) Math.floor(NUM_OPERATIONS / 2.0);

        List<Integer> items = new ArrayList<>(NUM_ENQUEUE);
        for (int i = 0; i < NUM_ENQUEUE; i++) {
            items.add(i + 1);
        }

        List<InsertionThread> insertionThreads = new ArrayList<>(NUM_ENQUEUE);
        for (int i = 0; i < NUM_ENQUEUE; i++) {
            Integer item = items.get(i);
            InsertionThread insertionThread = new InsertionThread(queue, item);
            insertionThreads.add(insertionThread);
        }

        List<DeletionThread> deletionThreads = new ArrayList<>(NUM_DEQUEUE);
        for (int i = 0; i < NUM_DEQUEUE; i++) {
            DeletionThread deletionThread = new DeletionThread(queue);
            deletionThreads.add(deletionThread);
        }

        for (int i = 0; i < NUM_ENQUEUE; i++) {
            insertionThreads.get(i).start();
        }

        for (int i = 0; i < NUM_DEQUEUE; i++) {
            deletionThreads.get(i).start();
        }

        for (int i = 0; i < NUM_ENQUEUE; i++) {
            try {
                insertionThreads.get(i).join();
            } catch (InterruptedException e) {

            }
        }

        for (int i = 0; i < NUM_DEQUEUE; i++) {
            try {
                deletionThreads.get(i).join();
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
     * HWQueue(n) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runHwQueueEnqueueTrust() {
        hwQueueEnqueueProgram(3);
    }

    /**
     * HWQueue(n/2, n/2) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runHwQueueEnqueueDequeueTrust() {
        hwQueueEnqueueDequeueProgram(3);
    }

    /**
     * LBQueue(n) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runLbQueueEnqueueTrust() {
        lbQueueEnqueueProgram(3);
    }

    /**
     * LBQueue(n/2, n/2) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runLbQueueEnqueueDequeueTrust() {
        lbQueueEnqueueDequeueProgram(3);
    }

    /**
     * UnboundedQueue(n) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runUbQueueEnqueueTrust() {
        ubQueueEnqueueProgram(3);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100,
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, strategy = "testor",
            budget = 2, timeout = 60000L)
    public void runUbQueueEnqueueTestor() {
        ubQueueEnqueueProgram(3);
    }

    /**
     * UnboundedQueue(n/2, n/2) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runUbQueueEnqueueDequeueTrust() {
        ubQueueEnqueueDequeueProgram(3);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100,
            schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, strategy = "testor",
            budget = 2, timeout = 60000L)
    public void runUbQueueEnqueueDequeueTestor() {
        ubQueueEnqueueDequeueProgram(3);
    }

    /**
     * LockBasedLinearPQueue(n) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runLbArrayPQueueEnqueueTrust() {
        lbArrayPQueueEnqueueProgram(3);
    }

    /**
     * LockBasedLinearPQueue(n/2, n/2) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runLbArrayPQueueEnqueueDequeueTrust() {
        lbArrayPQueueEnqueueDequeueProgram(3);
    }

    /**
     * MSQueue(n) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runMsQueueEnqueueTrust() {
        msQueueEnqueueProgram(3);
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runMsQueueEnqueueDequeueTrust() {
        msQueueEnqueueDequeueProgram(3);
    }
}
