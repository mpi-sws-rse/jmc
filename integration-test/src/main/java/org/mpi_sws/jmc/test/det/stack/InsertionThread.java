package org.mpi_sws.jmc.test.det.stack;

public class InsertionThread extends Thread {

    public Stack<Integer> stack;
    public int item;
    public int index = 0;

    public InsertionThread(Stack<Integer> stack, int item) {
        super();
        this.stack = stack;
        this.item = item;
        this.index = 0;
    }

    public InsertionThread() {
        super();
    }

    @Override
    public void run() {
        stack.push(item);
    }
}
