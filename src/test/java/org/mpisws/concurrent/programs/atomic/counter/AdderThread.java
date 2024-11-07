package org.mpisws.concurrent.programs.atomic.counter;

import org.mpisws.util.concurrent.AtomicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public class AdderThread extends Thread {
    private final AtomicInteger counter;

    public AdderThread(AtomicInteger counter) {
        this.counter = counter;
    }

    public void run() {
        try {
            counter.compareAndSet(0, 1);
        } catch (JMCInterruptException e) {
            System.out.println("Interrupted");
        }
    }
}
