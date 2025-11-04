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
        // Unwinded for one iteration
        int prior = lock.getAndDecrement();
        if (prior <= 0) {
            lock.getAndIncrement();
            JmcAssume.assume(lock.get() > 0);
            prior = lock.getAndDecrement();
            JmcAssume.assume(prior > 0);
        }
    }

    public void writeLock() {
        int prior = lock.getAndAdd(-RW_LOCK_BIAS);
        if (prior != RW_LOCK_BIAS) {
            lock.getAndAdd(RW_LOCK_BIAS); // undo
            JmcAssume.assume(lock.get() == RW_LOCK_BIAS);
            prior = lock.getAndAdd(-RW_LOCK_BIAS);
            JmcAssume.assume(prior == RW_LOCK_BIAS);
        }
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
        lock.getAndIncrement();
    }

    public void writeUnlock() {
        lock.getAndAdd(RW_LOCK_BIAS);
    }
}
