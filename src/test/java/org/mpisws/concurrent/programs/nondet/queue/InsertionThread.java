package org.mpisws.concurrent.programs.nondet.queue;

import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class InsertionThread extends Thread {

    public Queue queue;
    public SymbolicInteger value;

    public InsertionThread(Queue q, SymbolicInteger v) {
        queue = q;
        value = v;
    }

    public void run() {
        try {
            queue.enq(value);
        } catch (JMCInterruptException e) {

        }
    }
}
