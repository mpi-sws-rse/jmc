package org.mpi_sws.jmc.test.det.stack;

public class DeletionThread extends Thread {

    public Stack<Integer> stack;
    public int item;
    public int index = 0;

    public DeletionThread(Stack<Integer> stack, int item) {
        this.stack = stack;
        this.item = item;
        this.index = 0;
    }

    public DeletionThread(Stack<Integer> stack) {
        super();
        this.stack = stack;
    }

    public DeletionThread() {
        super();
    }

    @Override
    public void run() {
        stack.pop();
    }
}
