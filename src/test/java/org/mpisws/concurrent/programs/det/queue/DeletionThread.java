package org.mpisws.concurrent.programs.det.queue;

import org.mpisws.util.concurrent.JMCInterruptException;

public class DeletionThread extends Thread {

    public Queue queue;

    public DeletionThread(Queue q) {
        queue = q;
    }

    public void run() {
        try {
            queue.deq();
        } catch (JMCInterruptException e) {

        }
    }
}
