package org.mpi_sws.jmc.test.mpmcQueue;

public class WriterThread extends Thread {

    public final MPMCQueue queue;

    public WriterThread(MPMCQueue queue) {
        this.queue = queue;
    }


    @Override
    public void run() {
        Integer idx = queue.writePrepare();
        if (idx != null) {
            // Example write value
            // (Note: this is for single-writer demo. For multiple, use _array[] as pointer)
            queue.m_array[idx] = 1;
            queue.writePublish();
        }
    }
}
