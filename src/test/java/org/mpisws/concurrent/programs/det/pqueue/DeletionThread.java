package org.mpisws.concurrent.programs.det.pqueue;

import org.mpisws.util.concurrent.JMCInterruptException;

public class DeletionThread extends Thread {
    PQueue pqueue;
    int item;

    public void run() {
        try {
            item = pqueue.removeMin();
        } catch (JMCInterruptException e) {

        }
    }
}
