package org.mpi_sws.jmc.test.det.queue.abaMsQueue;

public class ReaderThread extends Thread {
    private final int id;
    private final AbaMsQueue queue;
    private final int[] output;
    private final boolean[] succ;

    public ReaderThread(int id, AbaMsQueue queue, int[] output, boolean[] succ) {
        this.id = id;
        this.queue = queue;
        this.output = output;
        this.succ = succ;
    }

    @Override
    public void run() {
        Integer result = queue.deq(id);
        if (result != null) {
            succ[id] = true;
            output[id] = result;
        } else {
            succ[id] = false;
            output[id] = -1; // Or some sentinel value
        }
    }
}