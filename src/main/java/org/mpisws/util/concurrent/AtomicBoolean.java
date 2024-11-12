package org.mpisws.util.concurrent;

import org.mpisws.runtime.JmcRuntime;

public class AtomicBoolean {

    public boolean value;
    public ReentrantLock lock = new ReentrantLock();

    public AtomicBoolean(boolean initialValue) {
        JmcRuntime.writeOperation(
                this,
                initialValue,
                Thread.currentThread(),
                "org/mpisws/util/concurrent/AtomicBoolean",
                "value",
                "Z");
        value = initialValue;
        JmcRuntime.waitRequest(Thread.currentThread());
    }

    public AtomicBoolean() {
        JmcRuntime.writeOperation(
                this,
                false,
                Thread.currentThread(),
                "org/mpisws/util/concurrent/AtomicBoolean",
                "value",
                "Z");
        value = false;
        JmcRuntime.waitRequest(Thread.currentThread());
    }

    public boolean get() {
        JmcRuntime.readOperation(
                this,
                Thread.currentThread(),
                "org/mpisws/util/concurrent/AtomicBoolean",
                "value",
                "Z");
        boolean result = value;
        JmcRuntime.waitRequest(Thread.currentThread());
        return result;
    }

    public void set(boolean newValue) {
        JmcRuntime.writeOperation(
                this,
                newValue,
                Thread.currentThread(),
                "org/mpisws/util/concurrent/AtomicBoolean",
                "value",
                "Z");
        value = newValue;
        JmcRuntime.waitRequest(Thread.currentThread());
    }

    public boolean compareAndSet(boolean expectedValue, boolean newValue)
            throws JMCInterruptException {
        lock.lock();
        try {
            JmcRuntime.readOperation(
                    this,
                    Thread.currentThread(),
                    "org/mpisws/util/concurrent/AtomicBoolean",
                    "value",
                    "Z");
            if (value == expectedValue) {
                JmcRuntime.waitRequest(Thread.currentThread());

                JmcRuntime.writeOperation(
                        this,
                        newValue,
                        Thread.currentThread(),
                        "org/mpisws/util/concurrent/AtomicBoolean",
                        "value",
                        "Z");
                value = newValue;
                JmcRuntime.waitRequest(Thread.currentThread());
                return true;
            }
            JmcRuntime.waitRequest(Thread.currentThread());
            return false;
        } finally {
            lock.unlock();
        }
    }
}
