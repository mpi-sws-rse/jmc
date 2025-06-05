package org.mpisws.jmc.util.concurrent;

public class JmcAtomicReferenceArray<V> {

    public V[] array;

    JmcReentrantLock lock = new JmcReentrantLock();

    public JmcAtomicReferenceArray(int length) {
        array = (V[]) new Object[length];
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
