package org.mpisws.util.concurrent;

public class AtomicInteger {

    public int value;
    public ReentrantLock lock = new ReentrantLock();

    public AtomicInteger(int initialValue) {
        value = initialValue;
    }

    public AtomicInteger() {
        value = 0;
    }

    public int get() {
        return value;
    }

    public void set(int newValue) {
        value = newValue;
    }

    public boolean compareAndSet(int expectedValue, int newValue) throws JMCInterruptException {
        lock.lock();
        try {
            if (value == expectedValue) {
                value = newValue;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }
}
