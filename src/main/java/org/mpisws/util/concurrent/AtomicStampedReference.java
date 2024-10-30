package org.mpisws.util.concurrent;

import org.mpisws.runtime.RuntimeEnvironment;

public class AtomicStampedReference<V> {

    public int stamp;

    public V value;

    public ReentrantLock lock = new ReentrantLock();

    public AtomicStampedReference(V initialValue, int initialStamp) {
        value = initialValue;
        stamp = initialStamp;
    }

    public boolean compareAndSet(V expectedReference, V newReference, int expectedStamp, int newStamp) throws JMCInterruptException {
        lock.lock();
        try {
            if (value == expectedReference && stamp == expectedStamp) {
                value = newReference;
                stamp = newStamp;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
            RuntimeEnvironment.waitRequest(Thread.currentThread());
        }
    }

    public V getReference() {
        return value;
    }

    public int getStamp() {
        return stamp;
    }

    public void set(V newReference, int newStamp) {
        value = newReference;
        stamp = newStamp;
    }


}
