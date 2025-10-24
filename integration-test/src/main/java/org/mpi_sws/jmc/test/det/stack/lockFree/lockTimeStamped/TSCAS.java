package org.mpi_sws.jmc.test.det.stack.lockFree.lockTimeStamped;


import java.util.concurrent.atomic.AtomicInteger;

public class TSCAS {

    public AtomicInteger counter = new AtomicInteger(1);

    public int newTimestamp() {
        return counter.getAndIncrement();
    }
}
