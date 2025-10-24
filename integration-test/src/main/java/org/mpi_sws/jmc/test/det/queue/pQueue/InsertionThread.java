package org.mpi_sws.jmc.test.det.queue.pQueue;

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
        pqueue.add(item, score);
    }
}
