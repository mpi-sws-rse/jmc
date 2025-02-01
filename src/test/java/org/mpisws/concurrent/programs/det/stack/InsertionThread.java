package org.mpisws.concurrent.programs.det.stack;

import org.mpisws.util.concurrent.JMCInterruptException;

public class InsertionThread extends Thread {

    public Stack<Integer> stack;
    public int item;
    public int index = 0;

    public InsertionThread(Stack<Integer> stack, int item) {
        this.stack = stack;
        this.item = item;
        this.index = 0;
    }

    public InsertionThread() {
    }

    @Override
    public void run() {
        try {
            stack.push(item);
        } catch (JMCInterruptException e) {
            //System.out.println("Interrupted");
        }
    }
}
