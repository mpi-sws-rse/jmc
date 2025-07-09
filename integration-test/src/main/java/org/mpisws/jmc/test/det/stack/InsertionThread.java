package org.mpisws.jmc.test.det.stack;

public class InsertionThread extends Thread {

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
    public void run() {
        stack.push(item);
    }
}
