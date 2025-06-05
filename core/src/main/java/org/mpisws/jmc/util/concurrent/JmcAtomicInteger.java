package org.mpisws.jmc.util.concurrent;

import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.JmcRuntimeUtils;

public class JmcAtomicInteger {

    public int value;
    public JmcReentrantLock lock = new JmcReentrantLock();

    public JmcAtomicInteger(int initialValue) {
        JmcRuntimeUtils.writeEventWithoutYield(this, initialValue,
                "org/mpisws/jmc/util/concurrent/JmcAtomicInteger", "value", "I");
        value = initialValue;
        JmcRuntime.yield();
    }

    public JmcAtomicInteger() {
        this(0);
    }

    public int get() {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(this,
                    "org/mpisws/jmc/util/concurrent/JmcAtomicInteger", "value", "I");
            int out = value;
            JmcRuntime.yield();
            return out;
        } finally {
            lock.unlock();
        }
    }

    public void set(int newValue) {
        lock.lock();
        try {
            JmcRuntimeUtils.writeEventWithoutYield(this, newValue,
                    "org/mpisws/jmc/util/concurrent/JmcAtomicInteger", "value", "I");
            value = newValue;
            JmcRuntime.yield();
        } finally {
            lock.unlock();
        }
    }

    public boolean compareAndSet(int expectedValue, int newValue) {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(this,
                    "org/mpisws/jmc/util/concurrent/JmcAtomicInteger", "value", "I");
            if (value == expectedValue) {
                JmcRuntime.yield();

                JmcRuntimeUtils.writeEventWithoutYield(this, newValue,
                        "org/mpisws/jmc/util/concurrent/JmcAtomicInteger", "value", "I");
                value = newValue;
                JmcRuntime.yield();
                return true;
            }
            JmcRuntime.yield();
            return false;
        } finally {
            lock.unlock();
        }
    }

    public int getAndIncrement() {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(this,
                    "org/mpisws/jmc/util/concurrent/JmcAtomicInteger", "value", "I");
            int result = value;
            JmcRuntime.yield();

            JmcRuntimeUtils.writeEventWithoutYield(this, result + 1,
                    "org/mpisws/jmc/util/concurrent/JmcAtomicInteger", "value", "I");
            value = result + 1;
            JmcRuntime.yield();
            return result;
        } finally {
            lock.unlock();
        }
    }

    public int getAndSet(int newValue) {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(this,
                    "org/mpisws/jmc/util/concurrent/JmcAtomicInteger", "value", "I");
            int oldValue = value;
            JmcRuntime.yield();

            JmcRuntimeUtils.writeEventWithoutYield(this, newValue,
                    "org/mpisws/jmc/util/concurrent/JmcAtomicInteger", "value", "I");
            value = newValue;
            JmcRuntime.yield();
            return oldValue;
        } finally {
            lock.unlock();
        }
    }

    public int addAndGet(int delta) {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(this,
                    "org/mpisws/jmc/util/concurrent/JmcAtomicInteger", "value", "I");
            int result = value;
            JmcRuntime.yield();

            JmcRuntimeUtils.writeEventWithoutYield(this, result + delta,
                    "org/mpisws/jmc/util/concurrent/JmcAtomicInteger", "value", "I");
            value = result + delta;
            JmcRuntime.yield();
            return value;
        } finally {
            lock.unlock();
        }
    }
}
