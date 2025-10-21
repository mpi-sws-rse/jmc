package org.mpi_sws.jmc.programs.det.stack;

import org.mpi_sws.jmc.api.util.concurrent.JmcThread;

public class InsertionThread extends JmcThread {

    public Stack<Integer> stack;
    public int item;

    public InsertionThread(Stack<Integer> stack, int item) {
        super();
        this.stack = stack;
        this.item = item;
    }

    public InsertionThread() {
        super();
    }

    @Override
    public void run1() {
        stack.push(item);
    }
}
