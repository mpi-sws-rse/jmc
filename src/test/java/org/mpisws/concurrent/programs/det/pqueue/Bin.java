package org.mpisws.concurrent.programs.det.pqueue;

import org.mpisws.util.concurrent.JMCInterruptException;

public interface Bin {
    void put(int item) throws JMCInterruptException;

    int get() throws JMCInterruptException;
}
