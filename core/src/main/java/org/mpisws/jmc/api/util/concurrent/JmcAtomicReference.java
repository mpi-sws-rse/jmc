package org.mpisws.jmc.api.util.concurrent;

import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.JmcRuntimeUtils;

/**
 * A redefinition of {@link java.util.concurrent.atomic.AtomicReference} to support JMC model
 * checking. This class provides atomic operations on a reference variable, ensuring thread safety
 * through the use of a reentrant lock.
 *
 * @param <V> the type of the reference held by this atomic reference
 */
public class JmcAtomicReference<V> {

    private V value;

    private final JmcReentrantLock lock;

    /**
     * Constructs a new JmcAtomicReference with the specified initial value.
     *
     * @param initialValue the initial value of the atomic reference
     */
    public JmcAtomicReference(V initialValue) {
        JmcRuntimeUtils.writeEventWithoutYield(
                this,
                initialValue,
                "org/mpisws/jmc/api/util/concurrent/JmcAtomicReference",
                "value",
                "Ljava/lang/Object;");
        value = initialValue;
        JmcRuntime.yield();
        JmcReentrantLock lock = new JmcReentrantLock();
        JmcRuntimeUtils.writeEventWithoutYield(
                this,
                lock,
                "org/mpisws/jmc/api/util/concurrent/JmcAtomicReference",
                "lock",
                "Lorg/mpisws/jmc/api/util/concurrent/JmcReentrantLock;");
        this.lock = lock;
        JmcRuntime.yield();
    }

    /** Constructs a new JmcAtomicReference with a null initial value. */
    public boolean compareAndSet(V expectedReference, V newReference) {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                    this,
                    "org/mpisws/jmc/api/util/concurrent/JmcAtomicReference",
                    "value",
                    "Ljava/lang/Object;");
            V readValue = value;
            JmcRuntime.yield();
            if (readValue == expectedReference) {
                JmcRuntimeUtils.writeEventWithoutYield(
                        this,
                        newReference,
                        "org/mpisws/jmc/api/util/concurrent/JmcAtomicReference",
                        "value",
                        "Ljava/lang/Object;");
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
            JmcRuntimeUtils.readEventWithoutYield(
                    this,
                    "org/mpisws/jmc/api/util/concurrent/JmcAtomicReference",
                    "value",
                    "Ljava/lang/Object;");
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
            JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    newValue,
                    "org/mpisws/jmc/api/util/concurrent/JmcAtomicReference",
                    "value",
                    "Ljava/lang/Object;");
            value = newValue;
            JmcRuntime.yield();
        } finally {
            lock.unlock();
        }
    }

    public V getAndSet(V newValue) {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                    this,
                    "org/mpisws/jmc/api/util/concurrent/JmcAtomicReference",
                    "value",
                    "Ljava/lang/Object;");
            V result = value;
            JmcRuntime.yield();

            JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    newValue,
                    "org/mpisws/jmc/api/util/concurrent/JmcAtomicReference",
                    "value",
                    "Ljava/lang/Object;");
            value = newValue;
            JmcRuntime.yield();
            return result;
        } finally {
            lock.unlock();
        }
    }
}
