package org.mpisws.concurrent.programs.det.stack.lockFree.simpleTSStack;

import org.mpisws.util.concurrent.AtomicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class TSCAS {

    public AtomicInteger counter = new AtomicInteger(1);

    public int newTimestamp() throws JMCInterruptException {
        return counter.getAndIncrement();
    }
}
