package org.mpisws.concurrent.programs.det.pqueue;

import org.mpisws.util.concurrent.JMCInterruptException;

public interface PQueue {
    void add(int item, int score) throws JMCInterruptException;

    int removeMin() throws JMCInterruptException;
}
