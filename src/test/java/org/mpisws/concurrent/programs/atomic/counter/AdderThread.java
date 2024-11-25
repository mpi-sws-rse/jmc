package org.mpisws.concurrent.programs.atomic.counter;

import org.mpisws.util.concurrent.AtomicInteger;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.JmcThread;

public class AdderThread extends JmcThread {
    private final AtomicInteger counter;

    public AdderThread(AtomicInteger counter) {
        super();
        this.counter = counter;
    }

    @Override
    public void run1() {
        try {
            counter.compareAndSet(0, 1);
        } catch (JMCInterruptException e) {
            System.out.println("Interrupted");
        }
    }
}
