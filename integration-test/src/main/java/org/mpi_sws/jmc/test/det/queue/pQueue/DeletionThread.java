package org.mpi_sws.jmc.test.det.queue.pQueue;

public class DeletionThread extends Thread {
    public PQueue pqueue;
    public int item;

    public void run() {
        item = pqueue.removeMin();
    }
}
