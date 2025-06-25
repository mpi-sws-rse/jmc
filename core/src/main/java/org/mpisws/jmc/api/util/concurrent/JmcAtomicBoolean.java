package org.mpisws.jmc.api.util.concurrent;

import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.JmcRuntimeUtils;

public class JmcAtomicBoolean {

    public boolean value;
    public JmcReentrantLock lock = new JmcReentrantLock();

    public JmcAtomicBoolean(boolean initialValue) {
        JmcRuntimeUtils.writeEventWithoutYield(this, initialValue,
                "org/mpisws/jmc/api/util/concurrent/JmcAtomicBoolean", "value", "Z");
        value = initialValue;
        JmcRuntime.yield();
    }

    public JmcAtomicBoolean() {
        this(false);
    }

    public boolean get() {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(this,
                    "org/mpisws/jmc/api/util/concurrent/JmcAtomicBoolean", "value", "Z");
            boolean out = value;
            JmcRuntime.yield();
            return out;
        } finally {
            lock.unlock();
        }
    }

    public void set(boolean newValue) {
        lock.lock();
        try {
            JmcRuntimeUtils.writeEventWithoutYield(this, newValue,
                    "org/mpisws/jmc/api/util/concurrent/JmcAtomicBoolean", "value", "Z");
            value = newValue;
            JmcRuntime.yield();
        } finally {
            lock.unlock();
        }
    }

    public boolean compareAndSet(boolean expectedValue, boolean newValue) {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(this,
                    "org/mpisws/jmc/api/util/concurrent/JmcAtomicBoolean", "value", "Z");
            if (value == expectedValue) {
                JmcRuntime.yield();

                JmcRuntimeUtils.writeEventWithoutYield(this, newValue,
                        "org/mpisws/jmc/api/util/concurrent/JmcAtomicBoolean", "value", "Z");
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
}
