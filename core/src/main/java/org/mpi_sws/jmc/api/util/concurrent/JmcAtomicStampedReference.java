package org.mpi_sws.jmc.api.util.concurrent;

import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils;

/**
 * A redefinition of {@link java.util.concurrent.atomic.AtomicStampedReference} for JMC model
 * checking.
 *
 * <p>Holds a reference plus an integer {@code stamp}, guarded by an internal {@link JmcReentrantLock}.
 * Each accessor reports the relevant read/write events (and yields) for the {@code value} and {@code
 * stamp} fields, so the paired reference/stamp update is atomic w.r.t. the schedule while its memory
 * events stay observable.
 *
 * @param <V> the type of the held reference
 */
// TODO : FIX THIS CLASS
public class JmcAtomicStampedReference<V> {

    /** The current stamp; accesses are reported as read/write events. */
    private int stamp;

    /** The held reference; accesses are reported as read/write events. */
    private V value;

    /** Internal lock making the paired reference/stamp operations atomic w.r.t. the schedule. */
    private final JmcReentrantLock lock;

    /**
     * Constructs a new stamped reference with the given initial reference and stamp, reporting the
     * initial writes of both fields.
     *
     * @param initialValue the initial reference
     * @param initialStamp the initial stamp
     */
    public JmcAtomicStampedReference(V initialValue, int initialStamp) {
        JmcRuntimeUtils.writeEventWithoutYield(
                this,
                initialValue,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference",
                "value",
                "Ljava/lang/Object;");
        value = initialValue;
        JmcRuntime.yield();

        JmcRuntimeUtils.writeEventWithoutYield(
                this,
                initialStamp,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference",
                "stamp",
                "I");
        stamp = initialStamp;
        JmcRuntime.yield();

        lock = new JmcReentrantLock();
    }

    /**
     * Atomically sets the reference and stamp if the current reference and stamp match the expected
     * ones (reporting the reads, and the writes on success, under the internal lock).
     *
     * @param expectedReference the expected current reference
     * @param newReference the new reference to set
     * @param expectedStamp the expected current stamp
     * @param newStamp the new stamp to set
     * @return {@code true} if updated, {@code false} otherwise
     */
    public boolean compareAndSet(
            V expectedReference, V newReference, int expectedStamp, int newStamp) {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                    this,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference",
                    "value",
                    "Ljava/lang/Object;");
            V readValue = value;
            JmcRuntime.yield();

            JmcRuntimeUtils.readEventWithoutYield(
                    this,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference",
                    "stamp",
                    "I");
            int readStamp = stamp;

            if (readValue == expectedReference && readStamp == expectedStamp) {
                JmcRuntime.yield();

                JmcRuntimeUtils.writeEventWithoutYield(
                        this,
                        newReference,
                        "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference",
                        "value",
                        "Ljava/lang/Object;");
                value = newReference;
                JmcRuntime.yield();

                JmcRuntimeUtils.writeEventWithoutYield(
                        this,
                        newStamp,
                        "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference",
                        "stamp",
                        "I");
                stamp = newStamp;
                JmcRuntime.yield();
                return true;
            }
            JmcRuntime.yield();
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the current reference (reporting a read event, under the internal lock).
     *
     * @return the current reference
     */
    public V getReference() {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                    this,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference",
                    "value",
                    "Ljava/lang/Object;");
            V result = value;
            JmcRuntime.yield();
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the current stamp (reporting a read event, under the internal lock).
     *
     * @return the current stamp
     */
    public int getStamp() {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                    this,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference",
                    "stamp",
                    "I");
            int result = stamp;
            JmcRuntime.yield();
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Unconditionally sets the reference and stamp (reporting both writes, under the internal lock).
     *
     * @param newReference the new reference
     * @param newStamp the new stamp
     */
    public void set(V newReference, int newStamp) {
        lock.lock();
        try {
            JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    newReference,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference",
                    "value",
                    "Ljava/lang/Object;");
            value = newReference;
            JmcRuntime.yield();

            JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    newStamp,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference",
                    "stamp",
                    "I");
            stamp = newStamp;
            JmcRuntime.yield();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the current reference and writes the current stamp into {@code stampHolder[0]}
     * (reporting both reads, under the internal lock).
     *
     * @param stampHolder a one-element array into which the current stamp is written
     * @return the current reference
     */
    public V get(int[] stampHolder) {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                    this,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference",
                    "value",
                    "Ljava/lang/Object;");
            V result = value;
            JmcRuntime.yield();

            JmcRuntimeUtils.readEventWithoutYield(
                    this,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicStampedReference",
                    "stamp",
                    "I");
            int resultStamp = stamp;
            JmcRuntime.yield();

            stampHolder[0] = resultStamp;
            return result;
        } finally {
            lock.unlock();
        }
    }
}
