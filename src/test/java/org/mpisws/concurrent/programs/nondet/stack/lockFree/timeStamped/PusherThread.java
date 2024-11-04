package org.mpisws.concurrent.programs.nondet.stack.lockFree.timeStamped;

import org.mpisws.concurrent.programs.nondet.stack.Stack;
import org.mpisws.util.concurrent.JMCInterruptException;

public class PusherThread extends Thread {

    public Stack<Integer> stack;
    public int item;
    public int id;

    public PusherThread() {

    }

    public PusherThread(Stack<Integer> stack, int item, int id) {
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