package org.mpisws.concurrent.programs.det.queue;

import org.mpisws.util.concurrent.JMCInterruptException;

public class InsertionThread extends Thread {

    Queue queue;
    int value;

    public InsertionThread(Queue q, int v) {
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
