package org.mpi_sws.jmc.api.util.concurrent;

import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
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

    private static final String OWNER = "org/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock";
    private static final String NAME = "token";
    private static final String DESCRIPTOR = "I";

    private int token;
    private int holdCount;
    private Thread owner;
    private final Object lockObj;

    public JmcReentrantLock() {
        JmcRuntimeUtils.writeEventWithoutYield(this, 0, OWNER, NAME, DESCRIPTOR);
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
        if (isInstrumentedSynchronizedBlock()) {
            token = 1;
            JmcRuntimeUtils.lockAcquiredEventWithoutYield(
                    getInstance(), OWNER, NAME, token, DESCRIPTOR, 1);
            return;
        }
        Thread currentThread = Thread.currentThread();
        synchronized (this) {
            if (owner == currentThread) {
                holdCount++;
                return;
            }
        }

        JmcRuntimeUtils.lockAcquireEvent(OWNER, NAME, token, DESCRIPTOR, getInstance());
        synchronized (this) {
            token = 1;
            owner = currentThread;
            holdCount = 1;
        }
        JmcRuntimeUtils.lockAcquiredEventWithoutYield(
                getInstance(), OWNER, NAME, token, DESCRIPTOR, 1);
    }

    /** Releases the lock. */
    public void unlock() {
        if (isInstrumentedSynchronizedBlock()) {
            token = 0;
            lockReleaseEventWithoutYield();
            return;
        }
        synchronized (this) {
            if (owner != Thread.currentThread()) {
                throw new IllegalMonitorStateException();
            }
            holdCount--;
            if (holdCount > 0) {
                return;
            }
            owner = null;
            token = 0;
        }
        JmcRuntimeUtils.lockReleaseEvent(getInstance(), OWNER, NAME, token, DESCRIPTOR, 0);
    }

    /**
     * Attempts to acquire the lock without blocking.
     *
     * <p>If the lock is currently held by another thread, returns {@code false} immediately.
     * If the lock is free (or held by the current thread for reentrancy), acquires it and
     * returns {@code true}.
     *
     * <p>This is a scheduling point — the scheduler controls which tasks run when,
     * so contention is explored naturally through interleaving.
     *
     * @return {@code true} if the lock was acquired, {@code false} otherwise
     */
    public synchronized boolean tryLock() {
        Thread currentThread = Thread.currentThread();
        if (owner == currentThread) {
            holdCount++;
            return true;
        }
        if (owner != null) {
            return false;
        }

        token = 1;
        owner = currentThread;
        holdCount = 1;
        JmcRuntimeUtils.lockAcquiredEventWithoutYield(
                getInstance(), OWNER, NAME, token, DESCRIPTOR, 1);
        return true;
    }

    /**
     * Attempts to acquire the lock, waiting up to the specified time.
     *
     * <p>For model checking purposes, the timeout is irrelevant — this delegates
     * to the non-blocking {@link #tryLock()} since JMC controls scheduling
     * deterministically and does not model real time.
     *
     * @param timeout the maximum time to wait (ignored under JMC)
     * @param unit the time unit (ignored under JMC)
     * @return {@code true} if the lock was acquired, {@code false} otherwise
     * @throws InterruptedException if the current thread is interrupted
     */
    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(unit);
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return tryLock();
    }

    /**
     * Returns whether the lock is currently held by any thread.
     *
     * @return {@code true} if the lock is held
     */
    public synchronized boolean isLocked() {
        return owner != null;
    }

    /**
     * Returns whether the lock is held by the current thread.
     *
     * @return {@code true} if the current thread holds this lock
     */
    public synchronized boolean isHeldByCurrentThread() {
        return owner == Thread.currentThread();
    }

    private boolean isInstrumentedSynchronizedBlock() {
        return lockObj != null;
    }

    private void lockReleaseEventWithoutYield() {
        var event = new JmcRuntimeEvent.Builder()
                .type(JmcRuntimeEvent.Type.LOCK_RELEASE_EVENT)
                .taskId(JmcRuntime.currentTask())
                .param("owner", OWNER)
                .param("name", NAME)
                .param("value", token)
                .param("descriptor", DESCRIPTOR)
                .param("newValue", 0)
                .param("instance", getInstance())
                .build();
        JmcRuntime.updateEvent(event);
    }
}