package org.mpisws.jmc.test.det.stack.lockFree.timeStamped;

import org.mpisws.jmc.test.det.stack.Stack;

public class PusherThread extends Thread {

    public Stack<Integer> stack;
    public int item;
    public int id;

    public PusherThread(Stack<Integer> stack, int item, int id) {
        super();
        this.stack = stack;
        this.item = item;
        this.id = id;
    }

    public PusherThread() {
        super();
    }

    @Override
    public void run() {
//        try {
            stack.push(item);
//        } catch (JMCInterruptException e) {
//            System.out.println("Interrupted");
//        }
    }
}
