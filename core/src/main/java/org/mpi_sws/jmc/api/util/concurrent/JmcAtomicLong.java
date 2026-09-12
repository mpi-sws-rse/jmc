package org.mpi_sws.jmc.api.util.concurrent;

import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils;

/**
 * A redefinition of {@link java.util.concurrent.atomic.AtomicLong} for JMC model checking.
 *
 * <p>Holds a {@code long} value plus an internal {@link JmcReentrantLock}. Simple {@code get}/{@code
 * set} report a single read/write event and yield; compound read-modify-write operations take the
 * lock, report their constituent read/write events (each followed by a yield), and release it — so
 * the update is atomic w.r.t. the schedule while its memory events stay observable.
 */
public class JmcAtomicLong {

    /** The held long value; every access is reported to the runtime as a read/write event. */
    private long value;
    /** Internal lock making the compound read-modify-write operations atomic w.r.t. the schedule. */
    private final JmcReentrantLock lock;

    /**
     * Constructs a new atomic long with the given initial value, reporting the initial writes of
     * {@code value} and {@code lock}.
     *
     * @param initialValue the initial value
     */
    public JmcAtomicLong(long initialValue) {
        JmcRuntimeUtils.writeEventWithoutYield(
                this,
                initialValue,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                "value",
                "J"
        );
        value = initialValue;
        JmcRuntime.yield();
        JmcReentrantLock lock = new JmcReentrantLock();
        JmcRuntimeUtils.writeEventWithoutYield(
                this,
                lock,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                "lock",
                "Lorg/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock;"
        );
        this.lock = lock;
        JmcRuntime.yield();
    }

    /** Constructs a new atomic long with an initial value of 0. */
    public JmcAtomicLong() {
        this(0L);
    }

    /**
     * Returns the current value (reporting a read event and yielding).
     *
     * @return the current value
     */
    public long get() {
        JmcRuntimeUtils.readEventWithoutYield(
                this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J");
        long out = value;
        JmcRuntime.yield();
        return out;
    }

    /**
     * Sets the value (reporting a write event and yielding).
     *
     * @param newValue the new value
     */
    public void set(long newValue) {
        JmcRuntimeUtils.writeEventWithoutYield(
                this,
                newValue,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                "value",
                "J");
        value = newValue;
        JmcRuntime.yield();
    }

    /**
     * Atomically sets the value to {@code update} if it currently equals {@code expect} (reporting the
     * read, and the write on success, under the internal lock).
     *
     * @param expect the expected current value
     * @param update the new value to set if the expectation holds
     * @return {@code true} if the value was updated, {@code false} otherwise
     */
    public boolean compareAndSet(long expect, long update) {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J");
            long currentValue = value;
            JmcRuntime.yield();
            if (currentValue == expect) {
                JmcRuntimeUtils.writeEventWithoutYield(
                        this,
                        update,
                        "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                        "value",
                        "J");
                value = update;
                JmcRuntime.yield();
                return true;
            } else {
                return false;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically increments the value by 1 and returns the previous value.
     *
     * @return the previous value
     */
    public long getAndIncrement() {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J");
            long result = value;
            JmcRuntime.yield();

            JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    result + 1,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                    "value",
                    "J");
            value = result + 1;
            JmcRuntime.yield();
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically sets the value and returns the previous value.
     *
     * @param newValue the new value
     * @return the previous value
     */
    public long getAndSet(long newValue) {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J");
            long oldValue = value;
            JmcRuntime.yield();

            JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    newValue,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                    "value",
                    "J");
            value = newValue;
            JmcRuntime.yield();
            return oldValue;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically adds the given delta and returns the updated value.
     *
     * @param delta the value to add
     * @return the updated value
     */
    public long addAndGet(long delta) {
        lock.lock();
        try {
            JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    value + delta,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                    "value",
                    "J");
            value = value + delta;
            JmcRuntime.yield();

            JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J");
            long result = value;
            JmcRuntime.yield();
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically adds the given delta and returns the previous value.
     *
     * @param delta the value to add
     * @return the previous value
     */
    public long getAndAdd(long delta) {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J");
            long result = value;
            JmcRuntime.yield();

            JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    result + delta,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                    "value",
                    "J");
            value = result + delta;
            JmcRuntime.yield();
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically increments the value by 1 and returns the updated value.
     *
     * @return the updated value
     */
    public long incrementAndGet() {
        lock.lock();
        try {
            JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    value + 1,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                    "value",
                    "J");
            value = value + 1;
            JmcRuntime.yield();

            JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J");
            long result = value;
            JmcRuntime.yield();
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically decrements the value by 1 and returns the previous value.
     *
     * @return the previous value
     */
    public long getAndDecrement() {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J");
            long result = value;
            JmcRuntime.yield();

            JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    result - 1,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                    "value",
                    "J");
            value = result - 1;
            JmcRuntime.yield();
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically decrements the value by 1 and returns the updated value.
     *
     * @return the updated value
     */
    public long decrementAndGet() {
        lock.lock();
        try {
            JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    value - 1,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                    "value",
                    "J");
            value = value - 1;
            JmcRuntime.yield();

            JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J");
            long result = value;
            JmcRuntime.yield();
            return result;
        } finally {
            lock.unlock();
        }
    }
}
