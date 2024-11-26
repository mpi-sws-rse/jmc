package org.mpisws.concurrent.programs.det.stack.lockFree.timeStamped;

import org.mpisws.concurrent.programs.det.stack.Stack;
import org.mpisws.util.concurrent.JmcThread;

public class PopperThread extends JmcThread {

    public Stack<Integer> stack;

    public PopperThread(Stack<Integer> stack) {
        super();
        this.stack = stack;
    }

    public PopperThread() {
        super();
    }

    @Override
    public void run1() {
        stack.pop();
    }
}
