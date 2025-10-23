package org.mpi_sws.jmc.test.estimation;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpi_sws.jmc.strategies.trust.TrustStrategy;
import org.mpi_sws.jmc.test.det.queue.HWQueue;
import org.mpi_sws.jmc.test.det.queue.Queue;
import org.mpi_sws.jmc.test.det.queue.DeletionThread;
import org.mpi_sws.jmc.test.det.queue.InsertionThread;

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
        stack_50_50_test(NUM_OPERATIONS, new HWQueue((int) Math.ceil(NUM_OPERATIONS / 2.0)));
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

    private void stack_50_50_test(int NUM_OPERATIONS, Queue queue) {
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
        hwQueueEnqueueProgram(4);
    }

    /**
     * HWQueue(n/2, n/2) test suite for n \in {2,4,6,8,10,12}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runHwQueueEnqueueDequeueTrust() {
        hwQueueEnqueueDequeueProgram(4);
    }
}
