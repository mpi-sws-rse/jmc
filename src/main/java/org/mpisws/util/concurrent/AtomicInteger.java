package org.mpisws.util.concurrent;

import org.mpisws.runtime.JmcRuntime;

public class AtomicInteger {

    public int value;
    public ReentrantLock lock = new ReentrantLock();

    public AtomicInteger(int initialValue) {
        JmcRuntime.writeOperation(
                this,
                initialValue,
                Thread.currentThread(),
                "org/mpisws/util/concurrent/AtomicInteger",
                "value",
                "I");
        value = initialValue;
        JmcRuntime.waitRequest(Thread.currentThread());
    }

    public AtomicInteger() {
        JmcRuntime.writeOperation(
                this,
                0,
                Thread.currentThread(),
                "org/mpisws/util/concurrent/AtomicInteger",
                "value",
                "I");
        value = 0;
        JmcRuntime.waitRequest(Thread.currentThread());
    }

    public int get() {
        JmcRuntime.readOperation(
                this,
                Thread.currentThread(),
                "org/mpisws/util/concurrent/AtomicInteger",
                "value",
                "I");
        int result = value;
        JmcRuntime.waitRequest(Thread.currentThread());
        return result;
    }

    public void set(int newValue) {
        JmcRuntime.writeOperation(
                this,
                newValue,
                Thread.currentThread(),
                "org/mpisws/util/concurrent/AtomicInteger",
                "value",
                "I");
        value = newValue;
        JmcRuntime.waitRequest(Thread.currentThread());
    }

    public boolean compareAndSet(int expectedValue, int newValue) throws JMCInterruptException {
        lock.lock();
        try {
            JmcRuntime.readOperation(
                    this,
                    Thread.currentThread(),
                    "org/mpisws/util/concurrent/AtomicInteger",
                    "value",
                    "I");
            if (value == expectedValue) {
                JmcRuntime.waitRequest(Thread.currentThread());

                JmcRuntime.writeOperation(
                        this,
                        newValue,
                        Thread.currentThread(),
                        "org/mpisws/util/concurrent/AtomicInteger",
                        "value",
                        "I");
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

    public int getAndIncrement() throws JMCInterruptException {
        lock.lock();
        try {
            JmcRuntime.readOperation(
                    this,
                    Thread.currentThread(),
                    "org/mpisws/util/concurrent/AtomicInteger",
                    "value",
                    "I");
            int result = value;
            JmcRuntime.waitRequest(Thread.currentThread());

            JmcRuntime.writeOperation(
                    this,
                    result + 1,
                    Thread.currentThread(),
                    "org/mpisws/util/concurrent/AtomicInteger",
                    "value",
                    "I");
            value = result + 1;
            JmcRuntime.waitRequest(Thread.currentThread());
            return result;
        } finally {
            lock.unlock();
        }
    }
}
