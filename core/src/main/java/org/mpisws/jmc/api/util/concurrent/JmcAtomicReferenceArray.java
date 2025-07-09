package org.mpisws.jmc.api.util.concurrent;

/**
 * A redefinition of the {@link java.util.concurrent.atomic.AtomicReferenceArray} class. This class
 * provides an array of references that can be atomically updated. It uses a {@link
 * JmcReentrantLock} to ensure thread safety.
 *
 * @param <V> the type of elements in this array
 */
public class JmcAtomicReferenceArray<V> {

    private V[] array;
    private final JmcReentrantLock lock;

    public JmcAtomicReferenceArray(int length) {
        // TODO: No initial write here.
        array = (V[]) new Object[length];
        lock = new JmcReentrantLock();
    }

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
}
