package org.mpisws.jmc.api.util.concurrent;

import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.JmcRuntimeUtils;

public class JmcAtomicReference<V> {

    public V value;

    JmcReentrantLock lock = new JmcReentrantLock();

    public JmcAtomicReference(V initialValue) {
        JmcRuntimeUtils.writeEventWithoutYield(this, initialValue,
                "org/mpisws/jmc/api/util/concurrent/JmcAtomicReference", "value", "Ljava/lang/Object;");
        value = initialValue;
        JmcRuntime.yield();
    }

    public boolean compareAndSet(V expectedReference, V newReference) {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(this,
                    "org/mpisws/jmc/api/util/concurrent/JmcAtomicReference", "value", "Ljava/lang/Object;");
            V readValue = value;
            JmcRuntime.yield();
            if (readValue == expectedReference) {
                JmcRuntimeUtils.writeEventWithoutYield(this, newReference,
                        "org/mpisws/jmc/api/util/concurrent/JmcAtomicReference", "value", "Ljava/lang/Object;");
                value = newReference;
                JmcRuntime.yield();
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public V get() {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(this,
                    "org/mpisws/jmc/api/util/concurrent/JmcAtomicReference", "value", "Ljava/lang/Object;");
            V result = value;
            JmcRuntime.yield();
            return result;
        } finally {
            lock.unlock();
        }
    }

    public void set(V newValue) {
        lock.lock();
        try {
            JmcRuntimeUtils.writeEventWithoutYield(this, newValue,
                    "org/mpisws/jmc/api/util/concurrent/JmcAtomicReference", "value", "Ljava/lang/Object;");
            value = newValue;
            JmcRuntime.yield();
        } finally {
            lock.unlock();
        }
    }

    public V getAndSet(V newValue) {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(this,
                    "org/mpisws/jmc/api/util/concurrent/JmcAtomicReference", "value", "Ljava/lang/Object;");
            V result = value;
            JmcRuntime.yield();

            JmcRuntimeUtils.writeEventWithoutYield(this, newValue,
                    "org/mpisws/jmc/api/util/concurrent/JmcAtomicReference", "value", "Ljava/lang/Object;");
            value = newValue;
            JmcRuntime.yield();
            return result;
        } finally {
            lock.unlock();
        }
    }
}
