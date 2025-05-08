package org.mpisws.jmc.util.concurrent;

import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.RuntimeEvent;

public class JmcAtomicStampedReference<V> {

    public int stamp;

    public V value;

    public JmcReentrantLock lock = new JmcReentrantLock();

    public JmcAtomicStampedReference(V initialValue, int initialStamp) {
        writeOp(initialValue, initialStamp);
        value = initialValue;
        stamp = initialStamp;
    }

    public boolean compareAndSet(
            V expectedReference, V newReference, int expectedStamp, int newStamp) {
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

    public void set(V newReference, int newStamp) {
        lock.lock();
        try {
            writeOp(newReference, newStamp);
            value = newReference;
            stamp = newStamp;
        } finally {
            lock.unlock();
        }
    }

    public V get(int[] stampHolder) {
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
                        .type(RuntimeEvent.Type.READ_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("owner", "org/mpisws/jmc/util/concurrent/AtomicStampedReference")
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
                        .type(RuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("owner", "org/mpisws/jmc/util/concurrent/AtomicStampedReference")
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
