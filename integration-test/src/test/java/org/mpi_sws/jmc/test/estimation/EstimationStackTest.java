package org.mpi_sws.jmc.test.estimation;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpi_sws.jmc.strategies.trust.TrustStrategy;
import org.mpi_sws.jmc.test.det.stack.DeletionThread;
import org.mpi_sws.jmc.test.det.stack.InsertionThread;
import org.mpi_sws.jmc.test.det.stack.Stack;
import org.mpi_sws.jmc.test.det.stack.agm.AGMStack;
import org.mpi_sws.jmc.test.det.stack.lockFree.LockFreeStack;
import org.mpi_sws.jmc.test.det.stack.treiber.TreiberStack;

import java.util.ArrayList;
import java.util.List;

public class EstimationStackTest {

    /**
     * AGMStack(n): This program starts n threads that each push an item onto an initially empty AGMStack.
     *
     * @param NUM_OPERATIONS number of operations
     */
    private void agmStackPushProgram(int NUM_OPERATIONS) {
        stack_100_test(NUM_OPERATIONS, new AGMStack<>(NUM_OPERATIONS));
    }

    /**
     * AGMStack(n/2, n/2): This program starts n/2 threads that each push an item onto an initially empty AGMStack,
     * and n/2 threads that each pop an item from the stack.
     *
     * @param NUM_OPERATIONS number of operations
     */
    private void agmStackPushPopProgram(int NUM_OPERATIONS) {
        stack_50_50_test(NUM_OPERATIONS, new AGMStack<>(NUM_OPERATIONS));
    }

    /**
     * TreiberStack(n): This program starts n threads that each push an item onto an initially empty TreiberStack.
     *
     * @param NUM_OPERATIONS number of operations
     */
    private void treiberStackPushProgram(int NUM_OPERATIONS) {
        stack_100_test(NUM_OPERATIONS, new TreiberStack<>());
    }

    /**
     * TreiberStack(n/2, n/2): This program starts n/2 threads that each push an item onto an initially empty TreiberStack,
     * and n/2 threads that each pop an item from the stack.
     *
     * @param NUM_OPERATIONS number of operations
     */
    private void treiberStackPushPopProgram(int NUM_OPERATIONS) {
        stack_50_50_test(NUM_OPERATIONS, new TreiberStack<>());
    }

    private void lockFreeStackPushProgram(int NUM_OPERATIONS) {
        stack_100_test(NUM_OPERATIONS, new LockFreeStack<>());
    }

    private void lockFreeStackPushPopProgram(int NUM_OPERATIONS) {
        stack_50_50_test(NUM_OPERATIONS, new LockFreeStack<>());
    }

    /**
     * ----------------------------------------------------
     * Helper methods to run the tests
     * ----------------------------------------------------
     */


    private void stack_100_test(int NUM_OPERATIONS, Stack<Integer> stack) {
        int NUM_PUSHES = NUM_OPERATIONS;

        List<Integer> items = new ArrayList<>(NUM_PUSHES);
        for (int i = 0; i < NUM_PUSHES; i++) {
            items.add(i + 1);
        }

        List<InsertionThread> pusherThreads = new ArrayList<>(NUM_PUSHES);
        for (int i = 0; i < NUM_PUSHES; i++) {
            Integer item = items.get(i);
            InsertionThread pusherThread = new InsertionThread();
            pusherThread.item = item;
            pusherThread.stack = stack;
            pusherThreads.add(pusherThread);
        }

        for (int i = 0; i < NUM_PUSHES; i++) {
            pusherThreads.get(i).start();
        }

        for (int i = 0; i < NUM_PUSHES; i++) {
            try {
                pusherThreads.get(i).join();
            } catch (InterruptedException e) {

            }
        }
    }

    private void stack_50_50_test(int NUM_OPERATIONS, Stack<Integer> stack) {
        int NUM_PUSHES = (int) Math.ceil(NUM_OPERATIONS / 2.0);
        int NUM_POPS = (int) Math.floor(NUM_OPERATIONS / 2.0);

        List<Integer> items = new ArrayList<>(NUM_PUSHES);
        for (int i = 0; i < NUM_PUSHES; i++) {
            items.add(i + 1);
        }

        List<InsertionThread> pusherThreads = new ArrayList<>(NUM_PUSHES);
        for (int i = 0; i < NUM_PUSHES; i++) {
            Integer item = items.get(i);
            InsertionThread pusherThread = new InsertionThread();
            pusherThread.item = item;
            pusherThread.stack = stack;
            pusherThreads.add(pusherThread);
        }

        List<DeletionThread> poperThreads = new ArrayList<>(NUM_POPS);
        for (int i = 0; i < NUM_POPS; i++) {
            DeletionThread poperThread = new DeletionThread();
            poperThread.stack = stack;
            poperThreads.add(poperThread);
        }

        for (int i = 0; i < NUM_PUSHES; i++) {
            pusherThreads.get(i).start();
        }

        for (int i = 0; i < NUM_POPS; i++) {
            poperThreads.get(i).start();
        }

        for (int i = 0; i < NUM_PUSHES; i++) {
            try {
                pusherThreads.get(i).join();
            } catch (InterruptedException e) {

            }
        }

        for (int i = 0; i < NUM_POPS; i++) {
            try {
                poperThreads.get(i).join();
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
     * AGMStackPush(n) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runAgmStackPushTrust() {
        agmStackPushProgram(6);
    }

    /**
     * AGMStackPushPop(n/2, n/2) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runAgmStackPushPopTrust() {
        agmStackPushPopProgram(6);
    }

    /**
     * TreiberStackPush(n) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runTreiberStackPushTrust() {
        treiberStackPushProgram(6);
    }

    /**
     * TreiberStackPushPop(n/2, n/2) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runTreiberStackPushPopTrust() {
        treiberStackPushPopProgram(6);
    }

    /**
     * LockFreeStackPush(n) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runLockFreeStackPushTrust() {
        lockFreeStackPushProgram(6);
    }

    /**
     * LockFreeStackPushPop(n/2, n/2) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runLockFreeStackPushPopTrust() {
        lockFreeStackPushPopProgram(6);
    }
}
