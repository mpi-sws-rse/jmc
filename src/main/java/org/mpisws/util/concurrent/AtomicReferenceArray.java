package org.mpisws.util.concurrent;

public class AtomicReferenceArray<V> {

    public V[] array;

    ReentrantLock lock = new ReentrantLock();

    public AtomicReferenceArray(int length) {
        array = (V[]) new Object[length];
    }

    public V getAndSet(int index, V newValue) throws JMCInterruptException {
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

    public void set(int index, V newValue) throws JMCInterruptException {
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
}
