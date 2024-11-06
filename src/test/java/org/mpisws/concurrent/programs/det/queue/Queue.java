package org.mpisws.concurrent.programs.det.queue;

import org.mpisws.util.concurrent.JMCInterruptException;

public interface Queue {

    void enq(int x) throws JMCInterruptException;

    int deq() throws JMCInterruptException;
}
