package org.mpisws.util.concurrent;

import org.mpisws.runtime.RuntimeEnvironment;

public class AtomicReference<V> {

    public V value;

    ReentrantLock lock = new ReentrantLock();

    public AtomicReference(V initialValue) {
        RuntimeEnvironment.writeOperation(
                this,
                initialValue,
                Thread.currentThread(),
                "org/mpisws/util/concurrent/AtomicReference",
                "value",
                "Ljava/lang/Object;");
        value = initialValue;
        RuntimeEnvironment.waitRequest(Thread.currentThread());
    }

    @Deprecated
    public boolean compareAndSetWL(V expectedReference, V newReference) {
        boolean result =
                RuntimeEnvironment.compareAndSetOperation(
                        value,
                        expectedReference,
                        newReference,
                        this,
                        Thread.currentThread(),
                        "org/mpisws/util/concurrent/AtomicReference",
                        "value",
                        "Ljava/lang/Object;");
        RuntimeEnvironment.waitRequest(Thread.currentThread());
        if (result) {
            value = newReference;
        }
        return result;
    }

    public boolean compareAndSet(V expectedReference, V newReference) throws JMCInterruptException {
        lock.lock();
        try {
            RuntimeEnvironment.readOperation(
                    this,
                    Thread.currentThread(),
                    "org/mpisws/util/concurrent/AtomicReference",
                    "value",
                    "Ljava/lang/Object;");
            if (value == expectedReference) {
                RuntimeEnvironment.waitRequest(Thread.currentThread());

                RuntimeEnvironment.writeOperation(
                        this,
                        newReference,
                        Thread.currentThread(),
                        "org/mpisws/util/concurrent/AtomicReference",
                        "value",
                        "Ljava/lang/Object;");
                value = newReference;
                RuntimeEnvironment.waitRequest(Thread.currentThread());
                return true;
            }
            RuntimeEnvironment.waitRequest(Thread.currentThread());
            return false;
        } finally {
            lock.unlock();
        }
    }

    public V get() {
        RuntimeEnvironment.readOperation(
                this,
                Thread.currentThread(),
                "org/mpisws/util/concurrent/AtomicReference",
                "value",
                "Ljava/lang/Object;");
        V result = value;
        RuntimeEnvironment.waitRequest(Thread.currentThread());
        return result;
    }

    public void set(V newValue) {
        RuntimeEnvironment.writeOperation(
                this,
                newValue,
                Thread.currentThread(),
                "org/mpisws/util/concurrent/AtomicReference",
                "value",
                "Ljava/lang/Object;");
        value = newValue;
        RuntimeEnvironment.waitRequest(Thread.currentThread());
    }
}
