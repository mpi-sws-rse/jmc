package org.mpisws.util.concurrent;

import org.mpisws.runtime.RuntimeEnvironment;

public class AtomicInteger {

    public int value;
    public ReentrantLock lock = new ReentrantLock();

    public AtomicInteger(int initialValue) {
        RuntimeEnvironment.writeOperation(
                this,
                initialValue,
                Thread.currentThread(),
                "org/mpisws/util/concurrent/AtomicInteger",
                "value",
                "I");
        value = initialValue;
        RuntimeEnvironment.waitRequest(Thread.currentThread());
    }

    public AtomicInteger() {
        RuntimeEnvironment.writeOperation(
                this,
                0,
                Thread.currentThread(),
                "org/mpisws/util/concurrent/AtomicInteger",
                "value",
                "I");
        value = 0;
        RuntimeEnvironment.waitRequest(Thread.currentThread());
    }

    public int get() {
        RuntimeEnvironment.readOperation(
                this,
                Thread.currentThread(),
                "org/mpisws/util/concurrent/AtomicInteger",
                "value",
                "I");
        int result = value;
        RuntimeEnvironment.waitRequest(Thread.currentThread());
        return result;
    }

    public void set(int newValue) {
        RuntimeEnvironment.writeOperation(
                this,
                newValue,
                Thread.currentThread(),
                "org/mpisws/util/concurrent/AtomicInteger",
                "value",
                "I");
        value = newValue;
        RuntimeEnvironment.waitRequest(Thread.currentThread());
    }

    public boolean compareAndSet(int expectedValue, int newValue) throws JMCInterruptException {
        lock.lock();
        try {
            RuntimeEnvironment.readOperation(
                    this,
                    Thread.currentThread(),
                    "org/mpisws/util/concurrent/AtomicInteger",
                    "value",
                    "I");
            if (value == expectedValue) {
                RuntimeEnvironment.waitRequest(Thread.currentThread());

                RuntimeEnvironment.writeOperation(
                        this,
                        newValue,
                        Thread.currentThread(),
                        "org/mpisws/util/concurrent/AtomicInteger",
                        "value",
                        "I");
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

    public int getAndIncrement() throws JMCInterruptException {
        lock.lock();
        try {
            RuntimeEnvironment.readOperation(
                    this,
                    Thread.currentThread(),
                    "org/mpisws/util/concurrent/AtomicInteger",
                    "value",
                    "I");
            int result = value;
            RuntimeEnvironment.waitRequest(Thread.currentThread());

            RuntimeEnvironment.writeOperation(
                    this,
                    result + 1,
                    Thread.currentThread(),
                    "org/mpisws/util/concurrent/AtomicInteger",
                    "value",
                    "I");
            value = result + 1;
            RuntimeEnvironment.waitRequest(Thread.currentThread());
            return result;
        } finally {
            lock.unlock();
        }
    }
}
