package org.mpisws.concurrent.programs.nondet.stack;

import org.mpisws.util.concurrent.JMCInterruptException;

public class DeletionThread extends Thread {

    public Stack<Integer> stack;

    public DeletionThread(Stack<Integer> stack) {
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
