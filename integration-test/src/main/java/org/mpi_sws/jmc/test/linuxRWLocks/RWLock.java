package org.mpi_sws.jmc.test.linuxRWLocks;

import org.mpi_sws.jmc.api.util.statements.JmcAssume;

import java.util.concurrent.atomic.AtomicInteger;

public class RWLock {
    private static final int RW_LOCK_BIAS = 0x00100000;
    private final AtomicInteger lock = new AtomicInteger(RW_LOCK_BIAS);

    public boolean readCanLock() {
        return lock.get() > 0;
    }

    public boolean writeCanLock() {
        return lock.get() == RW_LOCK_BIAS;
    }

    public void readLock() {
        int prior;
        // Unwinded for one iteration
        prior = lock.getAndDecrement();
        if (prior > 0) {
            return;
        }
        lock.incrementAndGet(); // undo
        JmcAssume.assume(lock.get() > 0);
    }

    public void writeLock() {
        int prior;
        // Unwinded for one iteration
        prior = lock.getAndAdd(-RW_LOCK_BIAS);
        if (prior == RW_LOCK_BIAS) {
            return;
        }
        lock.addAndGet(RW_LOCK_BIAS); // undo
        JmcAssume.assume(lock.get() == RW_LOCK_BIAS);
    }

    public boolean readTryLock() {
        int prior = lock.getAndDecrement();
        if (prior > 0) return true;
        lock.incrementAndGet(); // undo
        return false;
    }

    public boolean writeTryLock() {
        int prior = lock.getAndAdd(-RW_LOCK_BIAS);
        if (prior == RW_LOCK_BIAS) return true;
        lock.addAndGet(RW_LOCK_BIAS); // undo
        return false;
    }

    public void readUnlock() {
        lock.incrementAndGet();
    }

    public void writeUnlock() {
        lock.addAndGet(RW_LOCK_BIAS);
    }
}
