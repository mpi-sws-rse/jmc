package org.mpisws.concurrent.programs.nondet.stack;

import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class InsertionThread extends Thread {

    public Stack<SymbolicInteger> stack;
    public SymbolicInteger item;

    public InsertionThread(Stack<SymbolicInteger> stack, SymbolicInteger item) {
        this.stack = stack;
        this.item = item;
    }

    public InsertionThread() {
    }

    @Override
    public void run() {
        try {
            stack.push(item);
        } catch (JMCInterruptException e) {
            System.out.println("Interrupted");
        }
    }
}
