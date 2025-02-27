package org.mpisws.jmc.programs.det.stack;

import org.mpisws.jmc.util.concurrent.JmcThread;

public class DeletionThread extends JmcThread {

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
    public void run1() {
        stack.pop();
    }
}
