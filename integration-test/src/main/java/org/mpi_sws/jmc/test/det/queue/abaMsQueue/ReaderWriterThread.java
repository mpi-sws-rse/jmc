package org.mpi_sws.jmc.test.det.queue.abaMsQueue;

public class ReaderWriterThread extends Thread {
    private final int id;
    private final AbaMsQueue queue;
    private final int[] input;
    private final int[] output;
    private final boolean[] succ;

    public ReaderWriterThread(int id, AbaMsQueue queue, int[] input, int[] output, boolean[] succ) {
        this.id = id;
        this.queue = queue;
        this.input = input;
        this.output = output;
        this.succ = succ;
    }

    @Override
    public void run() {
        input[id] = id * 10;
        queue.enq(id, input[id]);
        Integer result = queue.deq(id);
        if (result != null) {
            succ[id] = true;
            output[id] = result;
        } else {
            succ[id] = false;
            output[id] = -1;
        }
    }
}