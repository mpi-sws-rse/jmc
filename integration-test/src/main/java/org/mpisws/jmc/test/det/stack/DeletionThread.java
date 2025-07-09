package org.mpisws.jmc.test.det.stack;

public class DeletionThread extends Thread {

    public Stack<Integer> stack;
    public int item;

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
