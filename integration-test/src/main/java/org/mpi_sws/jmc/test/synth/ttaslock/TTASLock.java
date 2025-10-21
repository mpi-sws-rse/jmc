package org.mpi_sws.jmc.test.synth.ttaslock;

import org.mpi_sws.jmc.api.util.statements.JmcAssume;

import java.util.concurrent.atomic.AtomicInteger;

public class TTASLock {

    private final AtomicInteger state = new AtomicInteger(0);

    public void init() {
        state.set(0);
    }

    private void awaitForLock() {
        // Modeling the busy-wait loop of TTAS lock
        JmcAssume.assume(state.get() == 0);
    }

    private boolean tryAcquire() {
        return state.getAndSet(1) == 1;
    }

    public void acquire() {
        while (true) {
            awaitForLock();
            if (!tryAcquire()) {
                return;
            }
        }
    }

    public void release() {
        state.set(0);
    }
}
