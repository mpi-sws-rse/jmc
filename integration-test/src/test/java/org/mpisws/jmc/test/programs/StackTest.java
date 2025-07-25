package org.mpisws.jmc.test.programs;

import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.annotations.JmcExpectExecutions;
import org.mpisws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpisws.jmc.test.det.stack.InsertionThread;
import org.mpisws.jmc.test.det.stack.Stack;
import org.mpisws.jmc.test.det.stack.lockFree.LockFreeStack;

import java.util.ArrayList;
import java.util.List;

public class StackTest {

    private void lockFreeStackTest(int numOperations) {
        Stack<Integer> stack = new LockFreeStack<>();

        List<Integer> items = new ArrayList<>(numOperations);
        for (int i = 0; i < numOperations; i++) {
            items.add(i);
        }

        List<InsertionThread> threads = new ArrayList<>(numOperations);
        for (int i = 0; i < numOperations; i++) {
            Integer item = items.get(i);
            InsertionThread thread = new InsertionThread();
            thread.stack = stack;
            thread.item = item;
            threads.add(thread);
        }

        for (int i = 0; i < numOperations; i++) {
            threads.get(i).start();
        }

        for (int i = 0; i < numOperations; i++) {
            try {
                threads.get(i).join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 100000)
    @JmcTrustStrategy
    @JmcExpectExecutions(2520) // For input 4
    public void runLockFreeStackTest_100_0_workload() {
        lockFreeStackTest(4);
    }
}
