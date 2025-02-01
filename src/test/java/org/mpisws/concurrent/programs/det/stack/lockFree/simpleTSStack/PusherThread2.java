package org.mpisws.concurrent.programs.det.stack.lockFree.simpleTSStack;

import org.mpisws.concurrent.programs.det.stack.Stack;
import org.mpisws.util.concurrent.JMCInterruptException;

public class PusherThread2 extends Thread {

    public Stack<Integer> stack;
    public int item;
    public int id;
    public int numOfItems = 2;

    public PusherThread2(Stack<Integer> stack, int item, int id) {
        this.stack = stack;
        this.item = item;
        this.id = id;
    }

    public PusherThread2() {
    }

    @Override
    public void run() {
        try {
            while (numOfItems > 0) {
                stack.push(item);
                numOfItems--;
            }
        } catch (JMCInterruptException e) {

        }
    }
}
