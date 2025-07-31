package org.mpi_sws.jmc.api.util.concurrent;

import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils;

import java.util.concurrent.locks.ReentrantLock;

/**
 * A reentrant lock that can be used to synchronize access to shared resources. Replacement for
 * {@link java.util.concurrent.locks.ReentrantLock}
 *
 * <p>Yields control to the runtime for lock and unlock.
 */
public class JmcReentrantLock {

    private int token = 0;
    private Object lockObj;
    private final ReentrantLock lock = new ReentrantLock();

    public JmcReentrantLock() {
        JmcRuntimeUtils.writeEventWithoutYield(
                this, 0, "org/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock", "token", "I");
        token = 0;
        JmcRuntime.yield();
        this.lockObj = null;
    }

    public JmcReentrantLock(Object lockObj) {
        this.lockObj = lockObj;
    }

    private Object getInstance() {
        if (lockObj == null) {
            return this;
        } else {
            return lockObj;
        }
    }

    /** Acquires the lock. */
    public void lock() {
        JmcRuntimeUtils.lockAcquireEvent(
                "org/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock",
                "token",
                token,
                "I",
                getInstance());

        lock.lock();

        JmcRuntimeUtils.lockAcquiredEventWithoutYield(
                getInstance(),
                "org/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock",
                "token",
                token,
                "I",
                1);
    }

    /** Releases the lock. */
    public void unlock() {
        lock.unlock();

        JmcRuntimeUtils.lockReleaseEvent(
                getInstance(),
                "org/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock",
                "token",
                token,
                "I",
                0);
    }
}
