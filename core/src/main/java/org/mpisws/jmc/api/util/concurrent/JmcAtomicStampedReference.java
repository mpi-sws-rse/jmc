package org.mpisws.jmc.api.util.concurrent;

import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.JmcRuntimeUtils;
import org.mpisws.jmc.runtime.RuntimeEvent;

public class JmcAtomicStampedReference<V> {

    public int stamp;

    public V value;

    public JmcReentrantLock lock = new JmcReentrantLock();

    public JmcAtomicStampedReference(V initialValue, int initialStamp) {
        JmcRuntimeUtils.writeEventWithoutYield(this, initialValue,
                "org/mpisws/jmc/api/util/concurrent/JmcAtomicStampedReference", "value", "Ljava/lang/Object;");
        value = initialValue;
        JmcRuntime.yield();

        JmcRuntimeUtils.writeEventWithoutYield(this, initialStamp,
                "org/mpisws/jmc/api/util/concurrent/JmcAtomicStampedReference", "stamp", "I");
        stamp = initialStamp;
        JmcRuntime.yield();
    }

    public boolean compareAndSet(
            V expectedReference, V newReference, int expectedStamp, int newStamp) {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(this,
                    "org/mpisws/jmc/api/util/concurrent/JmcAtomicStampedReference", "value", "Ljava/lang/Object;");
            V readValue = value;
            JmcRuntime.yield();

            JmcRuntimeUtils.readEventWithoutYield(this,
                    "org/mpisws/jmc/api/util/concurrent/JmcAtomicStampedReference", "stamp", "I");
            int readStamp = stamp;

            if (readValue == expectedReference && readStamp == expectedStamp) {
                JmcRuntime.yield();

                JmcRuntimeUtils.writeEventWithoutYield(this, newReference,
                        "org/mpisws/jmc/api/util/concurrent/JmcAtomicStampedReference", "value", "Ljava/lang/Object;");
                value = newReference;
                JmcRuntime.yield();

                JmcRuntimeUtils.writeEventWithoutYield(this, newStamp,
                        "org/mpisws/jmc/api/util/concurrent/JmcAtomicStampedReference", "stamp", "I");
                stamp = newStamp;
                JmcRuntime.yield();
                return true;
            }
            JmcRuntime.yield();
            return false;
        } finally {
            lock.unlock();
        }
    }

    public V getReference() {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(this,
                    "org/mpisws/jmc/api/util/concurrent/JmcAtomicStampedReference", "value", "Ljava/lang/Object;");
            V result = value;
            JmcRuntime.yield();
            return result;
        } finally {
            lock.unlock();
        }
    }

    public int getStamp() {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(this,
                    "org/mpisws/jmc/api/util/concurrent/JmcAtomicStampedReference", "stamp", "I");
            int result = stamp;
            JmcRuntime.yield();
            return result;
        } finally {
            lock.unlock();
        }
    }

    public void set(V newReference, int newStamp) {
        lock.lock();
        try {
            JmcRuntimeUtils.writeEventWithoutYield(this, newReference,
                    "org/mpisws/jmc/api/util/concurrent/JmcAtomicStampedReference", "value", "Ljava/lang/Object;");
            value = newReference;
            JmcRuntime.yield();

            JmcRuntimeUtils.writeEventWithoutYield(this, newStamp,
                    "org/mpisws/jmc/api/util/concurrent/JmcAtomicStampedReference", "stamp", "I");
            stamp = newStamp;
            JmcRuntime.yield();
        } finally {
            lock.unlock();
        }
    }

    public V get(int[] stampHolder) {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(this,
                    "org/mpisws/jmc/api/util/concurrent/JmcAtomicStampedReference", "value", "Ljava/lang/Object;");
            V result = value;
            JmcRuntime.yield();

            JmcRuntimeUtils.readEventWithoutYield(this,
                    "org/mpisws/jmc/api/util/concurrent/JmcAtomicStampedReference", "stamp", "I");
            int resultStamp = stamp;
            JmcRuntime.yield();

            stampHolder[0] = resultStamp;
            return result;
        } finally {
            lock.unlock();
        }
    }
}
