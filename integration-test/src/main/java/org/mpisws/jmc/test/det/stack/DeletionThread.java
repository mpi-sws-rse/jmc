package org.mpisws.jmc.test.det.stack;

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
        this.stack = stack;
    }

    public DeletionThread() {
    }

    @Override
    public void run() {
        stack.pop();
    }
}
