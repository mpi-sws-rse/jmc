package org.mpisws.jmc.programs.nondet.stack.lockFree.timeStamped;

import org.mpisws.jmc.programs.nondet.stack.Stack;

public class PoperThread extends Thread {

    public Stack<Integer> stack;

    public PoperThread(Stack<Integer> stack) {
        this.stack = stack;
    }

    public PoperThread() {}

    @Override
    public void run() {
//        try {
            stack.pop();
//        } catch (JMCInterruptException e) {
//            System.out.println("Interrupted");
//        }
    }
}
