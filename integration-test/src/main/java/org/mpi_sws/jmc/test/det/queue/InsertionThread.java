package org.mpi_sws.jmc.test.det.queue;

public class InsertionThread extends Thread {

    Queue queue;
    int value;

    public InsertionThread(Queue q, int v) {
        queue = q;
        value = v;
    }

    public void run() {
        queue.enq(value);
    }

}
