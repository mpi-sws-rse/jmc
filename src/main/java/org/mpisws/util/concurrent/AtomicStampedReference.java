package org.mpisws.util.concurrent;

import org.mpisws.runtime.JmcRuntime;
import org.mpisws.runtime.RuntimeEvent;
import org.mpisws.runtime.RuntimeEventType;

public class AtomicStampedReference<V> {

    public int stamp;

    public V value;

    public ReentrantLock lock = new ReentrantLock();

    public AtomicStampedReference(V initialValue, int initialStamp) {
        writeOp(initialValue, initialStamp);
        value = initialValue;
        stamp = initialStamp;
    }

    public boolean compareAndSet(
            V expectedReference, V newReference, int expectedStamp, int newStamp)
            throws JMCInterruptException {
        lock.lock();
        try {
            readOp();
            V readValue = value;
            int readStamp = stamp;

            if (readValue == expectedReference && readStamp == expectedStamp) {
                writeOp(newReference, newStamp);
                value = newReference;
                stamp = newStamp;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public V getReference() {
        readOp();
        V result = value;
        return result;
    }

    public int getStamp() {
        readOp();
        int result = stamp;
        return result;
    }

    public void set(V newReference, int newStamp) throws JMCInterruptException {
        lock.lock();
        try {
            writeOp(newReference, newStamp);
            value = newReference;
            stamp = newStamp;
        } finally {
            lock.unlock();
        }
    }

    public V get(int[] stampHolder) throws JMCInterruptException {
        lock.lock();
        try {
            readOp();
            V result = value;
            int resultStamp = stamp;

            stampHolder[0] = resultStamp;
            return result;
        } finally {
            lock.unlock();
        }
    }

    private void readOp() {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.READ_EVENT)
                        .threadId(JmcRuntime.currentThread())
                        .param("owner", "org/mpisws/util/concurrent/AtomicStampedReference")
                        .param("name", "value")
                        .param("descriptor", "Ljava/lang/Object;")
                        .param("instance", this)
                        .param("stamp", stamp)
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }

    private void writeOp(V newValue, int newStamp) {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.WRITE_EVENT)
                        .threadId(JmcRuntime.currentThread())
                        .param("owner", "org/mpisws/util/concurrent/AtomicStampedReference")
                        .param("name", "value")
                        .param("descriptor", "Ljava/lang/Object;")
                        .param("instance", this)
                        .param("stamp", stamp)
                        .param("newValue", newValue)
                        .param("newStamp", newStamp)
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }
}
