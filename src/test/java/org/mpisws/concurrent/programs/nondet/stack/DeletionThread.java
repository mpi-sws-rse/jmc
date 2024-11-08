package org.mpisws.concurrent.programs.nondet.stack;

import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class DeletionThread extends Thread {

    public Stack<SymbolicInteger> stack;

    public DeletionThread(Stack<SymbolicInteger> stack) {
        this.stack = stack;
    }

    public DeletionThread() {
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
