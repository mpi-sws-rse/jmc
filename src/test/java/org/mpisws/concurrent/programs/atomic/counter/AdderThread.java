package org.mpisws.concurrent.programs.atomic.counter;

import org.mpisws.runtime.HaltCheckerException;
import org.mpisws.runtime.HaltTaskException;
import org.mpisws.util.concurrent.JmcAtomicInteger;
import org.mpisws.util.concurrent.JmcThread;

public class AdderThread extends JmcThread {
    private final JmcAtomicInteger counter;

    public AdderThread(JmcAtomicInteger counter) {
        super();
        this.counter = counter;
    }

    @Override
    public void run1() {
        try {
            counter.compareAndSet(0, 1);
        } catch (HaltTaskException | HaltCheckerException e) {
            System.out.println("Interrupted");
        }
    }
}
