package org.mpisws.concurrent.programs.nondet.stack.lockFree.timeStamped;

import org.mpisws.concurrent.programs.nondet.stack.Stack;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class PoperThread extends Thread {

    public Stack<Integer> stack;
    public int id;

    public PoperThread(Stack<Integer> stack, int id) {
        this.stack = stack;
        this.id = id;
    }

    public PoperThread() {
    }

    @Override
    public void run() {
        try {
            stack.pop();
        } catch (JMCInterruptException e) {

        }
    }
}
