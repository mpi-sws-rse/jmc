package org.mpisws.concurrent.programs.det.pqueue;

import org.mpisws.util.concurrent.JMCInterruptException;

public class InsertionThread extends Thread {

    public PQueue pqueue;
    public int item;
    public int score;

    public InsertionThread(PQueue pqueue, int item, int score) {
        this.pqueue = pqueue;
        this.item = item;
        this.score = score;
    }

    public InsertionThread() {
    }

    public void run() {
        try {
            pqueue.add(item, score);
        } catch (JMCInterruptException e) {

        }
    }
}
