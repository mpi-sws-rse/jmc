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

    /** The JMC task ID that currently holds this lock, or null if unheld. */
    private Long holdingTask = null;

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
        JmcRuntimeUtils.lockAcquireEvent(
                "org/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock",
                "token",
                token,
                "I",
                getInstance());

        token = 1;
        holdingTask = JmcRuntime.currentTask();

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
        holdingTask = null;

        JmcRuntimeUtils.lockReleaseEvent(
                getInstance(),
                "org/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock",
                "token",
                token,
                "I",
                0);
    }

    /**
     * Attempts to acquire the lock without blocking.
     *
     * <p>If the lock is currently held by another JMC task, returns {@code false} immediately.
     * If the lock is free (or held by the current task for reentrancy), acquires it and
     * returns {@code true}.
     *
     * <p>This is a scheduling point — the scheduler controls which tasks run when,
     * so contention is explored naturally through interleaving.
     *
     * @return {@code true} if the lock was acquired, {@code false} otherwise
     */
    public boolean tryLock() {
        Long currentTask = JmcRuntime.currentTask();

        if (holdingTask != null && !holdingTask.equals(currentTask)) {
            // Lock is held by another task — emit a read event (scheduling point) and return false
            JmcRuntimeUtils.readEvent(
                    "org/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock",
                    "token",
                    "I",
                    getInstance());
            return false;
        }

        // Lock is free or held by current task (reentrant) — acquire it
        lock();
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
        return tryLock();
    }

    /**
     * Returns whether the lock is currently held by any task.
     *
     * @return {@code true} if the lock is held
     */
    public boolean isLocked() {
        return holdingTask != null;
    }

    /**
     * Returns whether the lock is held by the current JMC task.
     *
     * @return {@code true} if the current task holds this lock
     */
    public boolean isHeldByCurrentThread() {
        Long currentTask = JmcRuntime.currentTask();
        return currentTask.equals(holdingTask);
    }
}
