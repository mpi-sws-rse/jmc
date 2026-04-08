package org.mpi_sws.jmc.test.det.queue.pQueue;

public interface PQueue {
    void add(int item, int score);

    int removeMin();
}
