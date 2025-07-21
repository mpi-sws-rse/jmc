package org.mpisws.jmc.programs.det.stack.lockFree.timeStamped;

import org.mpisws.jmc.programs.det.stack.Stack;
import org.mpisws.jmc.api.util.concurrent.JmcThread;

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
