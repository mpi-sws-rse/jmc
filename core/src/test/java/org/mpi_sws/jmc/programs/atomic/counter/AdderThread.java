package org.mpi_sws.jmc.programs.atomic.counter;

import org.mpi_sws.jmc.runtime.HaltCheckerException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.api.util.concurrent.JmcAtomicInteger;
import org.mpi_sws.jmc.api.util.concurrent.JmcThread;

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
