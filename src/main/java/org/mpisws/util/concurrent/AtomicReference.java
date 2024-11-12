package org.mpisws.util.concurrent;

import org.mpisws.runtime.JmcRuntime;

public class AtomicReference<V> {

    public V value;

    ReentrantLock lock = new ReentrantLock();

    public AtomicReference(V initialValue) {
        JmcRuntime.writeOperation(
                this,
                initialValue,
                Thread.currentThread(),
                "org/mpisws/util/concurrent/AtomicReference",
                "value",
                "Ljava/lang/Object;");
        value = initialValue;
        JmcRuntime.waitRequest(Thread.currentThread());
    }

    @Deprecated
    public boolean compareAndSetWL(V expectedReference, V newReference) {
        boolean result =
                JmcRuntime.compareAndSetOperation(
                        value,
                        expectedReference,
                        newReference,
                        this,
                        Thread.currentThread(),
                        "org/mpisws/util/concurrent/AtomicReference",
                        "value",
                        "Ljava/lang/Object;");
        JmcRuntime.waitRequest(Thread.currentThread());
        if (result) {
            value = newReference;
        }
        return result;
    }

    public boolean compareAndSet(V expectedReference, V newReference) throws JMCInterruptException {
        lock.lock();
        try {
            JmcRuntime.readOperation(
                    this,
                    Thread.currentThread(),
                    "org/mpisws/util/concurrent/AtomicReference",
                    "value",
                    "Ljava/lang/Object;");
            if (value == expectedReference) {
                JmcRuntime.waitRequest(Thread.currentThread());

                JmcRuntime.writeOperation(
                        this,
                        newReference,
                        Thread.currentThread(),
                        "org/mpisws/util/concurrent/AtomicReference",
                        "value",
                        "Ljava/lang/Object;");
                value = newReference;
                JmcRuntime.waitRequest(Thread.currentThread());
                return true;
            }
            JmcRuntime.waitRequest(Thread.currentThread());
            return false;
        } finally {
            lock.unlock();
        }
    }

    public V get() {
        JmcRuntime.readOperation(
                this,
                Thread.currentThread(),
                "org/mpisws/util/concurrent/AtomicReference",
                "value",
                "Ljava/lang/Object;");
        V result = value;
        JmcRuntime.waitRequest(Thread.currentThread());
        return result;
    }

    public void set(V newValue) {
        JmcRuntime.writeOperation(
                this,
                newValue,
                Thread.currentThread(),
                "org/mpisws/util/concurrent/AtomicReference",
                "value",
                "Ljava/lang/Object;");
        value = newValue;
        JmcRuntime.waitRequest(Thread.currentThread());
    }
}
