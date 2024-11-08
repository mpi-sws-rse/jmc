package org.mpisws.concurrent.programs.nondet.stack.lockFree.timeStamped;

import org.mpisws.concurrent.programs.nondet.stack.Stack;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class PoperThread extends Thread {

    public Stack<SymbolicInteger> stack;

    public PoperThread(Stack<SymbolicInteger> stack) {
        this.stack = stack;
    }

    public PoperThread() {
    }

    @Override
    public void run() {
        try {
            stack.pop();
        } catch (JMCInterruptException e) {
            System.out.println("Interrupted");
        }
    }
}
