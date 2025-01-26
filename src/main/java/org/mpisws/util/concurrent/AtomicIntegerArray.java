package org.mpisws.util.concurrent;

public class AtomicIntegerArray {

    public int[] array;

    ReentrantLock lock = new ReentrantLock();

    public AtomicIntegerArray(int length) {
        array = new int[length];
    }

    public int getAndSet(int index, int newValue) throws JMCInterruptException {
        lock.lock();
        try {
            if (index < 0 || index >= array.length) {
                throw new ArrayIndexOutOfBoundsException(index);
            } else {
                int oldValue = array[index];
                array[index] = newValue;
                return oldValue;
            }
        } finally {
            lock.unlock();
        }
    }

    public void set(int index, int newValue) throws JMCInterruptException {
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

    public int get(int index) throws JMCInterruptException {
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
