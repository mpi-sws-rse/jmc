package org.mpisws.util.concurrent;

import org.mpisws.runtime.RuntimeEnvironment;


public class AtomicReference<V> {

    public V value;

    ReentrantLock lock = new ReentrantLock();

    public AtomicReference(V initialValue) {
        value = initialValue;
    }

    @Deprecated
    public boolean compareAndSetWL(V expectedReference, V newReference) {
        boolean result = RuntimeEnvironment.compareAndSetOperation(value, expectedReference, newReference, this,
                Thread.currentThread(), "org/mpisws/util/concurrent/AtomicReference", "value", "Ljava/lang/Object;");
        RuntimeEnvironment.waitRequest(Thread.currentThread());
        if (result) {
            value = newReference;
        }
        return result;
    }

    public boolean compareAndSet(V expectedReference, V newReference) throws JMCInterruptException {
        lock.lock();
        try {
            if (value == expectedReference) {
                value = newReference;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
            RuntimeEnvironment.waitRequest(Thread.currentThread());
        }
    }


    public V get() {
        return value;
    }

    public void set(V newValue) {
        value = newValue;
    }
}
