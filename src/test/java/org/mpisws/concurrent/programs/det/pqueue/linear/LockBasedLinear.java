package org.mpisws.concurrent.programs.det.pqueue.linear;

import org.mpisws.concurrent.programs.det.pqueue.Bin;
import org.mpisws.concurrent.programs.det.pqueue.LockBasedBin;
import org.mpisws.concurrent.programs.det.pqueue.PQueue;
import org.mpisws.util.concurrent.JMCInterruptException;

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

    public void add(int item, int score) throws JMCInterruptException {
        pqueue[score].put(item);
    }

    public int removeMin() throws JMCInterruptException {
        for (int i = 0; i < range; i++) {
            int item = pqueue[i].get();
            if (item != -1) {
                return item;
            }
        }
        return -1;
    }
}
