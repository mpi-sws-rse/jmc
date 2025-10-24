package org.mpi_sws.jmc.test.det.stack.lockFree.IntervalTimeStamped;

import org.mpi_sws.jmc.test.det.stack.Stack;

public class PusherThread extends Thread {

    public Stack<Integer> stack;
    public int item;
    public int id;

    public PusherThread(Stack<Integer> stack, int item, int id) {
        this.stack = stack;
        this.item = item;
        this.id = id;
    }

    public PusherThread() {
    }

    @Override
    public void run() {
        stack.push(item);
    }
}
