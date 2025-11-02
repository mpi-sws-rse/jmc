package org.mpi_sws.jmc.test.det.queue.abaMsQueue;

public class WriterThread extends Thread {
    private final int id;
    private final AbaMsQueue queue;
    private final int[] input;

    public WriterThread(int id, AbaMsQueue queue, int[] input) {
        this.id = id;
        this.queue = queue;
        this.input = input;
    }

    @Override
    public void run() {
        input[id] = id * 10;
        queue.enq(id, input[id]);
    }
}
