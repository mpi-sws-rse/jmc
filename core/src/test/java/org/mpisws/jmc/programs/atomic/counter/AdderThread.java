package org.mpisws.jmc.programs.atomic.counter;

import org.mpisws.jmc.runtime.HaltCheckerException;
import org.mpisws.jmc.runtime.HaltTaskException;
import org.mpisws.jmc.api.util.concurrent.JmcAtomicInteger;
import org.mpisws.jmc.api.util.concurrent.JmcThread;

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
