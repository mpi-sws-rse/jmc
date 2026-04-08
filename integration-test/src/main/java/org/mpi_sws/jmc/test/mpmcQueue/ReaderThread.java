package org.mpi_sws.jmc.test.mpmcQueue;

public class ReaderThread extends Thread {

    public final MPMCQueue queue;

    public ReaderThread(MPMCQueue queue) {
        this.queue = queue;
    }


    @Override
    public void run() {
        Integer val;
        while ((val = queue.readFetch()) != null) {
            // Read value is val
            queue.readConsume();
        }
    }
}
