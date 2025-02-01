package org.mpisws.concurrent.programs.nondet.stack.lockFree.timeStamped;

import org.mpisws.concurrent.programs.nondet.stack.Stack;
import org.mpisws.util.concurrent.JMCInterruptException;

public class PusherThread2 extends Thread {

    public Stack<Integer> stack;
    public int[] item;
    public int id;
    public int numOfPush = 2;

    public PusherThread2() {

    }

    public PusherThread2(Stack<Integer> stack, int[] item, int id) {
        this.stack = stack;
        this.item = item;
        this.id = id;
    }

    @Override
    public void run() {
        try {
            for (int i = 0; i < numOfPush; i++) {
                stack.push(item[i]);
            }
        } catch (JMCInterruptException e) {

        }
    }
}
