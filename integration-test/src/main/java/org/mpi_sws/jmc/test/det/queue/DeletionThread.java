package org.mpi_sws.jmc.test.det.queue;

public class DeletionThread extends Thread {

    public Queue queue;
    public int id;

    public DeletionThread(Queue q) {
        queue = q;
    }

    public void run() {
        queue.deq();
    }
}
