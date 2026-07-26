package org.mpi_sws.jmc.api.util.concurrent;

import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils;

/**
 * A redefinition of {@link java.util.concurrent.atomic.AtomicReference} to support JMC model
 * checking. This class provides atomic operations on a reference variable, ensuring thread safety
 * through the use of a reentrant lock.
 * TODO : FIX THIS CLASS
 *
 * @param <V> the type of the reference held by this atomic reference
 */
public class JmcAtomicReference<V> {

    /** The held reference; every access is reported to the runtime as a read/write event. */
    private V value;

    /** Internal lock making the compound {@code compareAndSet}/{@code getAndSet} atomic w.r.t. the schedule. */
    private final JmcReentrantLock lock;

    /**
     * Constructs a new JmcAtomicReference with a null initial value.
     */
    // Added because of iceberg error: java.util.concurrent.ExecutionException:
    //* java.lang.NoSuchMethodError: org.mpi_sws.jmc.api.util.concurrent.JmcAtomicReference:
    //method void <init>() not found */
    public JmcAtomicReference() {
        this(null);
    }

    /**
     * Constructs a new JmcAtomicReference with the specified initial value.
     *
     * @param initialValue the initial value of the atomic reference
     */
    public JmcAtomicReference(V initialValue) {
        JmcRuntimeUtils.writeEventWithoutYield(
                this,
                initialValue,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicReference",
                "value",
                "Ljava/lang/Object;");
        value = initialValue;
        JmcRuntime.yield();
        JmcReentrantLock lock = new JmcReentrantLock();
        JmcRuntimeUtils.writeEventWithoutYield(
                this,
                lock,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicReference",
                "lock",
                "Lorg/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock;");
        this.lock = lock;
        JmcRuntime.yield();
    }

    /**
     * Atomically sets the reference to {@code newReference} if it currently {@code ==}
     * {@code expectedReference} (reporting the read, and the write on success, under the internal
     * lock).
     *
     * @param expectedReference the expected current reference
     * @param newReference the new reference to set if the expectation holds
     * @return {@code true} if the reference was updated, {@code false} otherwise
     */
    public boolean compareAndSet(V expectedReference, V newReference) {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                    this,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicReference",
                    "value",
                    "Ljava/lang/Object;");
            V readValue = value;
            JmcRuntime.yield();
            if (readValue == expectedReference) {
                JmcRuntimeUtils.writeEventWithoutYield(
                        this,
                        newReference,
                        "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicReference",
                        "value",
                        "Ljava/lang/Object;");
                value = newReference;
                JmcRuntime.yield();
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the current reference (reporting a read event and yielding).
     *
     * @return the current reference
     */
    public V get() {
        JmcRuntimeUtils.readEventWithoutYield(
                this,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicReference",
                "value",
                "Ljava/lang/Object;");
        V result = value;
        JmcRuntime.yield();
        return result;
    }

    /**
     * Sets the reference (reporting a write event and yielding).
     *
     * @param newValue the new reference
     */
    public void set(V newValue) {
        JmcRuntimeUtils.writeEventWithoutYield(
                this,
                newValue,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicReference",
                "value",
                "Ljava/lang/Object;");
        value = newValue;
        JmcRuntime.yield();
    }

    /**
     * Atomically sets the reference and returns the previous one (reporting a read then a write
     * event, under the internal lock).
     *
     * @param newValue the new reference
     * @return the previous reference
     */
    public V getAndSet(V newValue) {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                    this,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicReference",
                    "value",
                    "Ljava/lang/Object;");
            V result = value;
            JmcRuntime.yield();

            JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    newValue,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicReference",
                    "value",
                    "Ljava/lang/Object;");
            value = newValue;
            JmcRuntime.yield();
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the default object string representation (the value is not read here).
     *
     * @return the identity-based string form
     */
    @Override
    public String toString() {
        return super.toString();
    }
}
