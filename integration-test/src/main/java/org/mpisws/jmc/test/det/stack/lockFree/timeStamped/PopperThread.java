package org.mpisws.jmc.test.det.stack.lockFree.timeStamped;

import org.mpisws.jmc.test.det.stack.Stack;

public class PopperThread extends Thread {

    public Stack<Integer> stack;

    public PopperThread(Stack<Integer> stack) {
        super();
        this.stack = stack;
    }

    public PopperThread() {
        super();
    }

    @Override
    public void run() {
        stack.pop();
    }
}
