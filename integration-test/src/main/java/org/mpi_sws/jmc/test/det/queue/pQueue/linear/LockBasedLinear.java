package org.mpi_sws.jmc.test.det.queue.pQueue.linear;

import org.mpi_sws.jmc.test.det.queue.pQueue.Bin;
import org.mpi_sws.jmc.test.det.queue.pQueue.LockBasedBin;
import org.mpi_sws.jmc.test.det.queue.pQueue.PQueue;

public class LockBasedLinear implements PQueue {

    int range;
    Bin[] pqueue;

    public LockBasedLinear(int range) {
        this.range = range;
        pqueue = new Bin[range];
        for (int i = 0; i < range; i++) {
            pqueue[i] = new LockBasedBin();
        }
    }

    public void add(int item, int score) {
        pqueue[score].put(item);
    }

    public int removeMin() {
        for (int i = 0; i < range; i++) {
            int item = pqueue[i].get();
            if (item != -1) {
                return item;
            }
        }
        return -1;
    }
}
