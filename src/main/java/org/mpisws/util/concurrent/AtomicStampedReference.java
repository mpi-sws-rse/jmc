package org.mpisws.util.concurrent;

import org.mpisws.runtime.JmcRuntime;

public class AtomicStampedReference<V> {

    public int stamp;

    public V value;

    public ReentrantLock lock = new ReentrantLock();

    public AtomicStampedReference(V initialValue, int initialStamp) {
        JmcRuntime.writeOperation(
                this,
                initialValue,
                Thread.currentThread(),
                "org/mpisws/util/concurrent/AtomicStampedReference",
                "value",
                "Ljava/lang/Object;");
        value = initialValue;
        JmcRuntime.waitRequest(Thread.currentThread());

        JmcRuntime.writeOperation(
                this,
                initialStamp,
                Thread.currentThread(),
                "org/mpisws/util/concurrent/AtomicStampedReference",
                "stamp",
                "I");
        stamp = initialStamp;
        JmcRuntime.waitRequest(Thread.currentThread());
    }

    public boolean compareAndSet(
            V expectedReference, V newReference, int expectedStamp, int newStamp)
            throws JMCInterruptException {
        lock.lock();
        try {
            JmcRuntime.readOperation(
                    this,
                    Thread.currentThread(),
                    "org/mpisws/util/concurrent/AtomicStampedReference",
                    "value",
                    "Ljava/lang/Object;");
            V readValue = value;
            JmcRuntime.waitRequest(Thread.currentThread());

            JmcRuntime.readOperation(
                    this,
                    Thread.currentThread(),
                    "org/mpisws/util/concurrent/AtomicStampedReference",
                    "stamp",
                    "I");
            int readStamp = stamp;

            if (readValue == expectedReference && readStamp == expectedStamp) {
                JmcRuntime.waitRequest(Thread.currentThread());

                JmcRuntime.writeOperation(
                        this,
                        newReference,
                        Thread.currentThread(),
                        "org/mpisws/util/concurrent/AtomicStampedReference",
                        "value",
                        "Ljava/lang/Object;");
                value = newReference;
                JmcRuntime.waitRequest(Thread.currentThread());

                JmcRuntime.writeOperation(
                        this,
                        newStamp,
                        Thread.currentThread(),
                        "org/mpisws/util/concurrent/AtomicStampedReference",
                        "stamp",
                        "I");
                stamp = newStamp;
                JmcRuntime.waitRequest(Thread.currentThread());
                return true;
            }
            JmcRuntime.waitRequest(Thread.currentThread());
            return false;
        } finally {
            lock.unlock();
        }
    }

    public V getReference() {
        JmcRuntime.readOperation(
                this,
                Thread.currentThread(),
                "org/mpisws/util/concurrent/AtomicStampedReference",
                "value",
                "Ljava/lang/Object;");
        V result = value;
        JmcRuntime.waitRequest(Thread.currentThread());
        return result;
    }

    public int getStamp() {
        JmcRuntime.readOperation(
                this,
                Thread.currentThread(),
                "org/mpisws/util/concurrent/AtomicStampedReference",
                "stamp",
                "I");
        int result = stamp;
        JmcRuntime.waitRequest(Thread.currentThread());
        return result;
    }

    public void set(V newReference, int newStamp) throws JMCInterruptException {
        lock.lock();
        try {
            JmcRuntime.writeOperation(
                    this,
                    newReference,
                    Thread.currentThread(),
                    "org/mpisws/util/concurrent/AtomicStampedReference",
                    "value",
                    "Ljava/lang/Object;");
            value = newReference;
            JmcRuntime.waitRequest(Thread.currentThread());

            JmcRuntime.writeOperation(
                    this,
                    newStamp,
                    Thread.currentThread(),
                    "org/mpisws/util/concurrent/AtomicStampedReference",
                    "stamp",
                    "I");
            stamp = newStamp;
            JmcRuntime.waitRequest(Thread.currentThread());
        } finally {
            lock.unlock();
        }
    }

    public V get(int[] stampHolder) throws JMCInterruptException {
        lock.lock();
        try {
            JmcRuntime.readOperation(
                    this,
                    Thread.currentThread(),
                    "org/mpisws/util/concurrent/AtomicStampedReference",
                    "value",
                    "Ljava/lang/Object;");
            V result = value;
            JmcRuntime.waitRequest(Thread.currentThread());

            JmcRuntime.readOperation(
                    this,
                    Thread.currentThread(),
                    "org/mpisws/util/concurrent/AtomicStampedReference",
                    "stamp",
                    "I");
            int resultStamp = stamp;
            JmcRuntime.waitRequest(Thread.currentThread());

            stampHolder[0] = resultStamp;
            return result;
        } finally {
            lock.unlock();
        }
    }
}
