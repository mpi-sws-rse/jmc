package org.mpisws.concurrent.programs.det.stack;

import org.mpisws.util.concurrent.JMCInterruptException;

public class InsersionThread extends Thread {

    public Stack<Integer> stack;
    public int item;

    public InsersionThread(Stack<Integer> stack, int item) {
        this.stack = stack;
        this.item = item;
    }

    public InsersionThread() {
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
