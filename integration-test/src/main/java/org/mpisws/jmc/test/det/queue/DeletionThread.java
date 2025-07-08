package org.mpisws.jmc.test.det.queue;

public class DeletionThread extends Thread {

    public Queue queue;

    public DeletionThread(Queue q) {
        queue = q;
    }

    public void run() {
        queue.deq();
    }
}
