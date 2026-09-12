package org.mpi_sws.jmc.api.util.concurrent;

/**
 * A redefinition of the {@link java.util.concurrent.atomic.AtomicReferenceArray} class. This class
 * provides an array of references that can be atomically updated. It uses a {@link
 * JmcReentrantLock} to ensure thread safety.
 * TODO: Currently, this implementation does not communicate with the JMC runtime.
 * TODO : FIX THIS CLASS
 *
 * @param <V> the type of elements in this array
 */
public class JmcAtomicReferenceArray<V> {

    /** Backing array of references, guarded by {@link #lock}. */
    private final V[] array;
    /** Internal lock serializing element accesses. */
    private final JmcReentrantLock lock;

    /**
     * Creates an atomic reference array of the given length.
     *
     * <p>Note: unlike the other atomics, this class does not (yet) report read/write events for the
     * elements — accesses are only serialized by the internal lock (see the class {@code TODO}).
     *
     * @param length the array length
     */
    public JmcAtomicReferenceArray(int length) {
        // TODO: No initial write here.
        array = (V[]) new Object[length];
        lock = new JmcReentrantLock();
    }

    /**
     * Atomically sets the element at {@code index} and returns the previous value, under the internal
     * lock.
     *
     * @param index the element index
     * @param newValue the new value
     * @return the previous value at that index
     * @throws ArrayIndexOutOfBoundsException if {@code index} is out of range
     */
    public V getAndSet(int index, V newValue) {
        lock.lock();
        try {
            if (index < 0 || index >= array.length) {
                throw new ArrayIndexOutOfBoundsException(index);
            } else {
                V oldValue = array[index];
                array[index] = newValue;
                return oldValue;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the element at {@code index}, under the internal lock.
     *
     * @param index the element index
     * @param newValue the new value
     * @throws ArrayIndexOutOfBoundsException if {@code index} is out of range
     */
    public void set(int index, V newValue) {
        lock.lock();
        try {
            if (index < 0 || index >= array.length) {
                throw new ArrayIndexOutOfBoundsException(index);
            } else {
                array[index] = newValue;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the element at {@code index}, under the internal lock.
     *
     * @param index the element index
     * @return the value at that index
     * @throws ArrayIndexOutOfBoundsException if {@code index} is out of range
     */
    public V get(int index) {
        lock.lock();
        try {
            if (index < 0 || index >= array.length) {
                throw new ArrayIndexOutOfBoundsException(index);
            } else {
                return array[index];
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the array length.
     *
     * @return the number of elements
     */
    public int length() {
        return array.length;
    }
}
