package org.mpi_sws.jmc.test.det.stack.lockFree.IntervalTimeStamped;

import org.mpi_sws.jmc.test.det.stack.Stack;

public class PoperThread extends Thread {

    public Stack<Integer> stack;

    public PoperThread(Stack<Integer> stack) {
        this.stack = stack;
    }

    public PoperThread() {
    }

    @Override
    public void run() {
        stack.pop();
    }
}
