package org.mpi_sws.jmc.api.util.concurrent;

import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * A reentrant lock that can be used to synchronize access to shared resources. Replacement for
 * {@link java.util.concurrent.locks.ReentrantLock}
 *
 * <p>Yields control to the runtime for lock and unlock.
 */
public class JmcReentrantLock {

    private int token = 0;
    private final Object lockObj;

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

    /** Returns the instance to be used for locking. */
    public Object getInstance() {
        return Objects.requireNonNullElse(lockObj, this);
    }

    /** Acquires the lock. */
    public void lock() {
        JmcRuntimeUtils.fairBlockingLockAcquireEvent(
                "org/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock",
                "token",
                token,
                "I",
                getInstance());

        token = 1;
        // Removing call to an actual reentrant lock
        // lock.lock();
        // Since we use the same primitive for synchronized blocks with wait/notify,
        // we cannot do actual lock and unlock here and block.
        // Instead, we just yield to the runtime to handle the locking.
        // The runtime will manage which task has the lock and which are waiting.

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
        token = 0;

        JmcRuntimeUtils.lockReleaseEvent(
                getInstance(),
                "org/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock",
                "token",
                token,
                "I",
                0);
    }


    public boolean tryLock() {
        boolean result = JmcRuntimeUtils.unfairNonblockingLockAcquireEvent(
                "org/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock",
                "token",
                token,
                "I",
                getInstance());
        if (result) {
            token = 1;
            JmcRuntimeUtils.lockAcquiredEventWithoutYield(
                    getInstance(),
                    "org/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock",
                    "token",
                    token,
                    "I",
                    1);
            return true;
        }
        return false;
    }

    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        // We simply ignore the timeout
        boolean res = JmcRuntimeUtils.fairNonblockingLockAcquireEvent(
                "org/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock",
                "token",
                token,
                "I",
                getInstance());
        if (res) {
            token = 1;
            JmcRuntimeUtils.lockAcquiredEventWithoutYield(
                    getInstance(),
                    "org/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock",
                    "token",
                    token,
                    "I",
                    1);
            return true;
        }
        return false;
    }

    public boolean isLocked() {
        return JmcRuntimeUtils.isLockedEvent(
                "org/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock",
                "token",
                token,
                "I",
                getInstance());
    }

    public boolean isHeldByCurrentThread() {
        return JmcRuntimeUtils.isLockOwnerEvent(
                "org/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock",
                "token",
                token,
                "I",
                getInstance());
    }
}
