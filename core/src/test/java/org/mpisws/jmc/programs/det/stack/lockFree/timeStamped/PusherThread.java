package org.mpisws.jmc.programs.det.stack.lockFree.timeStamped;

import org.mpisws.jmc.programs.det.stack.Stack;
import org.mpisws.jmc.util.concurrent.JmcThread;

public class PusherThread extends JmcThread {

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
    public void run1() {
//        try {
            stack.push(item);
//        } catch (JMCInterruptException e) {
//            System.out.println("Interrupted");
//        }
    }
}
