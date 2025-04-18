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
        RuntimeEnvironment.writeOperation(this, 0, Thread.currentThread(), "org/mpisws/util/concurrent/AtomicInteger", "value", "I");
        value = 0;
        RuntimeEnvironment.waitRequest(Thread.currentThread());
    }

    public int get() throws JMCInterruptException {
        lock.lock();
        try {
            RuntimeEnvironment.readOperation(this, Thread.currentThread(), "org/mpisws/util/concurrent/AtomicInteger", "value", "I");
            int result = value;
            RuntimeEnvironment.waitRequest(Thread.currentThread());
            return result;
        } finally {
            lock.unlock();
        }
    }

    public void set(int newValue) throws JMCInterruptException {
        lock.lock();
        try {
            RuntimeEnvironment.writeOperation(this, newValue, Thread.currentThread(), "org/mpisws/util/concurrent/AtomicInteger", "value", "I");
            value = newValue;
            RuntimeEnvironment.waitRequest(Thread.currentThread());
        } finally {
            lock.unlock();
        }
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

    public int getAndIncrement() throws JMCInterruptException {
        lock.lock();
        try {
            RuntimeEnvironment.readOperation(this, Thread.currentThread(), "org/mpisws/util/concurrent/AtomicInteger", "value", "I");
            int result = value;
            RuntimeEnvironment.waitRequest(Thread.currentThread());

            RuntimeEnvironment.writeOperation(this, result + 1, Thread.currentThread(), "org/mpisws/util/concurrent/AtomicInteger", "value", "I");
            value = result + 1;
            RuntimeEnvironment.waitRequest(Thread.currentThread());
            return result;
        } finally {
            lock.unlock();
        }
    }

    public int getAndSet(int newValue) throws JMCInterruptException {
        lock.lock();
        try {
            RuntimeEnvironment.readOperation(this, Thread.currentThread(), "org/mpisws/util/concurrent/AtomicInteger", "value", "I");
            int result = value;
            RuntimeEnvironment.waitRequest(Thread.currentThread());

            RuntimeEnvironment.writeOperation(this, newValue, Thread.currentThread(), "org/mpisws/util/concurrent/AtomicInteger", "value", "I");
            value = newValue;
            RuntimeEnvironment.waitRequest(Thread.currentThread());
            return result;
        } finally {
            lock.unlock();
        }
    }

    public int addAndGet(int delta) throws JMCInterruptException {
        lock.lock();
        try {
            RuntimeEnvironment.readOperation(this, Thread.currentThread(), "org/mpisws/util/concurrent/AtomicInteger", "value", "I");
            int result = value;
            RuntimeEnvironment.waitRequest(Thread.currentThread());

            RuntimeEnvironment.writeOperation(this, result + delta, Thread.currentThread(), "org/mpisws/util/concurrent/AtomicInteger", "value", "I");
            value = result + delta;
            RuntimeEnvironment.waitRequest(Thread.currentThread());
            return value;
        } finally {
            lock.unlock();
        }
    }
}
