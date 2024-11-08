package org.mpisws.concurrent.programs.nondet.stack.lockFree.timeStamped;

import org.mpisws.concurrent.programs.nondet.stack.Stack;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class PusherThread extends Thread {

    public Stack<SymbolicInteger> stack;
    public SymbolicInteger item;
    public int id;

    public PusherThread() {

    }

    public PusherThread(Stack<SymbolicInteger> stack, SymbolicInteger item, int id) {
        this.stack = stack;
        this.item = item;
        this.id = id;
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
