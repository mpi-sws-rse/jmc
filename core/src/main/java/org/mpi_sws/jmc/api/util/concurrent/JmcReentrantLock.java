package org.mpi_sws.jmc.api.util.concurrent;

import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils;
import java.util.Objects;

/**
 * A reentrant lock that can be used to synchronize access to shared resources. Replacement for
 * {@link java.util.concurrent.locks.ReentrantLock}
 *
 * <p>Yields control to the runtime for lock and unlock.
 */
public class JmcReentrantLock {

    /** Lock state modelled as a plain field ({@code 0} = free, {@code 1} = held) whose reads/writes are reported. */
    private int token = 0;
    /** The monitor object this lock stands in for (for {@code synchronized} blocks), or {@code null} when it is a standalone lock. */
    private final Object lockObj;

    /**
     * Creates a standalone lock (the replacement for {@code new ReentrantLock()}). Reports the initial
     * write of {@code token} and yields.
     */
    public JmcReentrantLock() {
        JmcRuntimeUtils.writeEventWithoutYield(
                this, 0, "org/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock", "token", "I");
        token = 0;
        JmcRuntime.yield();
        this.lockObj = null;
    }

    /**
     * Creates a lock that stands in for an existing monitor object; used to back instrumented {@code
     * synchronized} blocks and {@code wait}/{@code notify} on {@code lockObj}.
     *
     * @param lockObj the monitor object this lock represents
     */
    public JmcReentrantLock(Object lockObj) {
        this.lockObj = lockObj;
    }

    /**
     * Returns the object used as the lock identity: the wrapped monitor object if any, otherwise this
     * lock. This is the value reported as the lock {@code instance} in lock events.
     *
     * @return the monitor object, or {@code this}
     */
    public Object getInstance() {
        return Objects.requireNonNullElse(lockObj, this);
    }

    /**
     * Acquires the lock (replacement for {@code ReentrantLock.lock()}).
     *
     * <p>Reports a {@code LOCK_ACQUIRE_EVENT} (which yields, letting {@code TrackLocks} decide whether
     * this task may proceed or must block), then marks the lock held and reports a {@code
     * LOCK_ACQUIRED_EVENT}. No real JVM lock is taken — ownership and blocking are managed by the
     * runtime, which is what lets the scheduler serialize contended locks.
     */
    public void lock() {
        JmcRuntimeUtils.lockAcquireEvent(
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

    /**
     * Releases the lock (replacement for {@code ReentrantLock.unlock()}). Marks the lock free and
     * reports a {@code LOCK_RELEASE_EVENT}, which makes {@code TrackLocks} re-activate any tasks
     * waiting on it.
     */
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
}
