package org.mpi_sws.jmc.test.estimation;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpi_sws.jmc.strategies.trust.TrustStrategy;
import org.mpi_sws.jmc.test.det.stack.DeletionThread;
import org.mpi_sws.jmc.test.det.stack.InsertionThread;
import org.mpi_sws.jmc.test.det.stack.Stack;
import org.mpi_sws.jmc.test.det.stack.agm.AGMStack;
import org.mpi_sws.jmc.test.det.stack.lockFree.IntervalTimeStamped.ITSStack;
import org.mpi_sws.jmc.test.det.stack.lockFree.IntervalTimeStamped.PoperThread;
import org.mpi_sws.jmc.test.det.stack.lockFree.IntervalTimeStamped.PusherThread;
import org.mpi_sws.jmc.test.det.stack.lockFree.LockFreeStack;
import org.mpi_sws.jmc.test.det.stack.lockFree.elimination.EliminationBackoffStack;
import org.mpi_sws.jmc.test.det.stack.lockFree.atomicTimeStamped.ATSStack;
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

    private void backoffEliminationStackPushProgram(int NUM_OPERATIONS) {
        int[] arr = new int[NUM_OPERATIONS]; // Over data domain
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            arr[i] = i % 2; // data domain {0,1}
        }
        Stack stack = new EliminationBackoffStack<Integer>(NUM_OPERATIONS);

        List<Integer> items = new ArrayList<>(NUM_OPERATIONS);
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            items.add(i);
        }

        List<InsertionThread> threads = new ArrayList<>(NUM_OPERATIONS);
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            Integer item = items.get(i);
            InsertionThread thread = new InsertionThread();
            thread.stack = stack;
            thread.item = item;
            thread.index = arr[i];
            threads.add(thread);
        }

        for (int i = 0; i < NUM_OPERATIONS; i++) {
            threads.get(i).start();
        }

        for (int i = 0; i < NUM_OPERATIONS; i++) {
            try {
                threads.get(i).join();
            } catch (InterruptedException e) {

            }
        }
    }

    private void backoffEliminationStackPushPopProgram(int NUM_OPERATIONS) {
        int[] arr = new int[NUM_OPERATIONS]; // Over data domain
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            arr[i] = i % 2; // data domain {0,1}
        }
        int NUM_PUSHES = (int) Math.ceil(NUM_OPERATIONS / 2.0);
        int NUM_POPS = (int) Math.floor(NUM_OPERATIONS / 2.0);
        Stack stack = new EliminationBackoffStack<Integer>(NUM_PUSHES);

        List<Integer> items = new ArrayList<>(NUM_PUSHES);
        for (int i = 0; i < NUM_PUSHES; i++) {
            items.add(i);
        }

        List<InsertionThread> threads = new ArrayList<>(NUM_PUSHES);
        for (int i = 0; i < NUM_PUSHES; i++) {
            Integer item = items.get(i);
            InsertionThread thread = new InsertionThread();
            thread.stack = stack;
            thread.item = item;
            thread.index = arr[i];
            threads.add(thread);
        }

        List<DeletionThread> popThreads = new ArrayList<>(NUM_POPS);
        for (int i = 0; i < NUM_POPS; i++) {
            Integer item = items.get(i);
            DeletionThread thread = new DeletionThread();
            thread.stack = stack;
            thread.item = item;
            thread.index = arr[i + NUM_PUSHES];
            popThreads.add(thread);
        }

        for (int i = 0; i < NUM_PUSHES; i++) {
            threads.get(i).start();
        }

        for (int i = 0; i < NUM_POPS; i++) {
            popThreads.get(i).start();
        }

        for (int i = 0; i < NUM_PUSHES; i++) {
            try {
                threads.get(i).join();
            } catch (InterruptedException e) {

            }
        }

        for (int i = 0; i < NUM_POPS; i++) {
            try {
                popThreads.get(i).join();
            } catch (InterruptedException e) {

            }
        }
    }

    private void IntervalTimestampedStackPushProgram(int NUM_OPERATIONS) {
        long[] threadIds = new long[NUM_OPERATIONS];
        List<Integer> items = new ArrayList<>(NUM_OPERATIONS);
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            items.add(i);
        }

        List<PusherThread> threads = new ArrayList<>(NUM_OPERATIONS);
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            Integer item = items.get(i);
            PusherThread thread = new PusherThread();
            thread.item = item;
            thread.id = i;
            threadIds[i] = thread.id;
            threads.add(thread);
        }

        Stack stack = new ITSStack(NUM_OPERATIONS, threadIds);

        for (int i = 0; i < NUM_OPERATIONS; i++) {
            threads.get(i).stack = stack;
        }

        for (int i = 0; i < NUM_OPERATIONS; i++) {
            threads.get(i).start();
        }

        for (int i = 0; i < NUM_OPERATIONS; i++) {
            try {
                threads.get(i).join();
            } catch (InterruptedException e) {

            }
        }
    }

    private void IntervalTimestampedStackPushPopProgram(int NUM_OPERATIONS) {
        long[] threadIds = new long[NUM_OPERATIONS];
        int NUM_PUSHES = (int) Math.ceil(NUM_OPERATIONS / 2.0);
        int NUM_POPS = (int) Math.floor(NUM_OPERATIONS / 2.0);

        List<Integer> items = new ArrayList<>(NUM_OPERATIONS);
        for (int i = 0; i < NUM_PUSHES; i++) {
            items.add(i);
        }

        List<PusherThread> pusherThreads = new ArrayList<>(NUM_PUSHES);
        for (int i = 0; i < NUM_PUSHES; i++) {
            Integer item = items.get(i);
            PusherThread pusherThread = new PusherThread();
            pusherThread.item = item;
            pusherThread.id = i;
            threadIds[i] = pusherThread.id;
            pusherThreads.add(pusherThread);
        }

        Stack stack = new ITSStack(NUM_PUSHES, threadIds);

        for (int i = 0; i < NUM_PUSHES; i++) {
            pusherThreads.get(i).stack = stack;
        }

        List<PoperThread> poperThreads = new ArrayList<>(NUM_POPS);
        for (int i = 0; i < NUM_POPS; i++) {
            PoperThread poperThread = new PoperThread();
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

    private void AtomicTimestampedStackPushProgram(int NUM_OPERATIONS) {
        long[] threadIds = new long[NUM_OPERATIONS];
        List<Integer> items = new ArrayList<>(NUM_OPERATIONS);
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            items.add(i);
        }

        List<PusherThread> threads = new ArrayList<>(NUM_OPERATIONS);
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            Integer item = items.get(i);
            PusherThread thread = new PusherThread();
            thread.item = item;
            thread.id = i;
            threadIds[i] = thread.id;
            threads.add(thread);
        }

        Stack stack = new ATSStack(NUM_OPERATIONS, threadIds);

        for (int i = 0; i < NUM_OPERATIONS; i++) {
            threads.get(i).stack = stack;
        }

        for (int i = 0; i < NUM_OPERATIONS; i++) {
            threads.get(i).start();
        }

        for (int i = 0; i < NUM_OPERATIONS; i++) {
            try {
                threads.get(i).join();
            } catch (InterruptedException e) {

            }
        }
    }

    private void AtomicTimestampedStackPushPopProgram(int NUM_OPERATIONS) {
        long[] threadIds = new long[NUM_OPERATIONS];
        int NUM_PUSHES = (int) Math.ceil(NUM_OPERATIONS / 2.0);
        int NUM_POPS = (int) Math.floor(NUM_OPERATIONS / 2.0);

        List<Integer> items = new ArrayList<>(NUM_OPERATIONS);
        for (int i = 0; i < NUM_PUSHES; i++) {
            items.add(i);
        }

        List<PusherThread> pusherThreads = new ArrayList<>(NUM_PUSHES);
        for (int i = 0; i < NUM_PUSHES; i++) {
            Integer item = items.get(i);
            PusherThread pusherThread = new PusherThread();
            pusherThread.item = item;
            pusherThread.id = i;
            threadIds[i] = pusherThread.id;
            pusherThreads.add(pusherThread);
        }

        Stack stack = new ATSStack(NUM_PUSHES, threadIds);

        for (int i = 0; i < NUM_PUSHES; i++) {
            pusherThreads.get(i).stack = stack;
        }

        List<PoperThread> poperThreads = new ArrayList<>(NUM_POPS);
        for (int i = 0; i < NUM_POPS; i++) {
            PoperThread poperThread = new PoperThread();
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
                //e.printStackTrace();
            }
        }

        for (int i = 0; i < NUM_POPS; i++) {
            try {
                poperThreads.get(i).join();
            } catch (InterruptedException e) {
                //e.printStackTrace();
            }
        }
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
        agmStackPushProgram(3);
    }

    /**
     * AGMStackPushPop(n/2, n/2) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runAgmStackPushPopTrust() {
        agmStackPushPopProgram(3);
    }

    /**
     * TreiberStackPush(n) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runTreiberStackPushTrust() {
        treiberStackPushProgram(3);
    }

    /**
     * TreiberStackPushPop(n/2, n/2) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runTreiberStackPushPopTrust() {
        treiberStackPushPopProgram(3);
    }

    /**
     * LockFreeStackPush(n) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runLockFreeStackPushTrust() {
        lockFreeStackPushProgram(3);
    }

    /**
     * LockFreeStackPushPop(n/2, n/2) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runLockFreeStackPushPopTrust() {
        lockFreeStackPushPopProgram(3);
    }

    /**
     * BackoffEliminationStackPush(n) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runBackoffEliminationStackPushTrust() {
        backoffEliminationStackPushProgram(3);
    }

    /**
     * BackoffEliminationStackPushPop(n/2, n/2) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runBackoffEliminationStackPushPopTrust() {
        backoffEliminationStackPushPopProgram(3);
    }

    /**
     * IntervalTimestampedStackPush(n) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runIntervalTimestampedStackPushTrust() {
        IntervalTimestampedStackPushProgram(3);
    }

    /**
     * IntervalTimestampedStackPushPop(n/2, n/2) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runIntervalTimestampedStackPushPopTrust() {
        IntervalTimestampedStackPushPopProgram(3);
    }

    /**
     * AtomicTimestampedStackPush(n) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runAtomicBasedTimestampedStackPushTrust() {
        AtomicTimestampedStackPushProgram(3);
    }

    /**
     * AtomicTimestampedStackPushPop(n/2, n/2) test suite for n \in {2,3,4,5,6}
     * 1. TruSt model checking
     */
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 1000000, debug = false)
    @JmcTrustStrategy(schedulingPolicy = TrustStrategy.SchedulingPolicy.FIFO, loggerTree = true)
    public void runAtomicTimestampedStackPushPopTrust() {
        AtomicTimestampedStackPushPopProgram(3);
    }
}
