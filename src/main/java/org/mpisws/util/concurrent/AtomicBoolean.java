package org.mpisws.util.concurrent;

import org.mpisws.runtime.RuntimeEnvironment;

public class AtomicBoolean {

    public boolean value;
    public ReentrantLock lock = new ReentrantLock();

    public AtomicBoolean(boolean initialValue) {
        RuntimeEnvironment.writeOperation(this, initialValue, Thread.currentThread(), "org/mpisws/util/concurrent/AtomicBoolean", "value", "Z");
        value = initialValue;
        RuntimeEnvironment.waitRequest(Thread.currentThread());
    }

    public AtomicBoolean() {
        RuntimeEnvironment.writeOperation(this, false, Thread.currentThread(), "org/mpisws/util/concurrent/AtomicBoolean", "value", "Z");
        value = false;
        RuntimeEnvironment.waitRequest(Thread.currentThread());
    }

    public boolean get() throws JMCInterruptException {
        lock.lock();
        try {
            RuntimeEnvironment.readOperation(this, Thread.currentThread(), "org/mpisws/util/concurrent/AtomicBoolean", "value", "Z");
            boolean result = value;
            RuntimeEnvironment.waitRequest(Thread.currentThread());
            return result;
        } finally {
            lock.unlock();
        }
    }

    public void set(boolean newValue) throws JMCInterruptException {
        lock.lock();
        try {
            RuntimeEnvironment.writeOperation(this, newValue, Thread.currentThread(), "org/mpisws/util/concurrent/AtomicBoolean", "value", "Z");
            value = newValue;
            RuntimeEnvironment.waitRequest(Thread.currentThread());
        } finally {
            lock.unlock();
        }
    }

    public boolean compareAndSet(boolean expectedValue, boolean newValue) throws JMCInterruptException {
        lock.lock();
        try {
            RuntimeEnvironment.readOperation(this, Thread.currentThread(), "org/mpisws/util/concurrent/AtomicBoolean", "value", "Z");
            if (value == expectedValue) {
                RuntimeEnvironment.waitRequest(Thread.currentThread());

                RuntimeEnvironment.writeOperation(this, newValue, Thread.currentThread(), "org/mpisws/util/concurrent/AtomicBoolean", "value", "Z");
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
