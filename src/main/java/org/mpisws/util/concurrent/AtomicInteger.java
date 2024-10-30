package org.mpisws.util.concurrent;

import org.mpisws.runtime.RuntimeEnvironment;

public class AtomicInteger {

    public int value;
    public ReentrantLock lock = new ReentrantLock();

    public AtomicInteger(int initialValue) {
        RuntimeEnvironment.writeOperation(this, initialValue, Thread.currentThread(), "org/mpisws/util/concurrent/AtomicInteger", "value", "I");
        value = initialValue;
        RuntimeEnvironment.waitRequest(Thread.currentThread());
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
            RuntimeEnvironment.readOperation(this, Thread.currentThread(), "org/mpisws/util/concurrent/AtomicInteger", "value", "I");
            if (value == expectedValue) {
                RuntimeEnvironment.waitRequest(Thread.currentThread());

                RuntimeEnvironment.writeOperation(this, newValue, Thread.currentThread(), "org/mpisws/util/concurrent/AtomicInteger", "value", "I");
                value = newValue;
                RuntimeEnvironment.waitRequest(Thread.currentThread());
                return true;
            }
            RuntimeEnvironment.waitRequest(Thread.currentThread());
            return false;
        } finally {
            lock.unlock();
        }
    }
}
