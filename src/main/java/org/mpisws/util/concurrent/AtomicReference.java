package org.mpisws.util.concurrent;

import org.mpisws.runtime.JmcRuntime;
import org.mpisws.runtime.RuntimeEvent;
import org.mpisws.runtime.RuntimeEventType;

import java.util.HashMap;

public class AtomicReference<V> {

    public V value;

    ReentrantLock lock = new ReentrantLock();

    public AtomicReference(V initialValue) {
        writeOp(initialValue);
        value = initialValue;
    }

    public boolean compareAndSet(V expectedReference, V newReference) throws JMCInterruptException {
        lock.lock();
        try {
            readOp();
            if (value == expectedReference) {
                writeOp(newReference);
                value = newReference;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public V get() {
        readOp();
        return value;
    }

    public void set(V newValue) {
        writeOp(newValue);
        value = newValue;
    }

    private void writeOp(V newValue) {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.WRITE_EVENT)
                        .threadId(JmcRuntime.currentThread())
                        .params(
                                new HashMap<>() {
                                    {
                                        put("newValue", newValue);
                                        put("owner", "org/mpisws/util/concurrent/AtomicReference");
                                        put("name", "value");
                                        put("descriptor", "Ljava/lang/Object;");
                                    }
                                })
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }

    private void readOp() {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.READ_EVENT)
                        .threadId(JmcRuntime.currentThread())
                        .params(
                                new HashMap<>() {
                                    {
                                        put("owner", "org/mpisws/util/concurrent/AtomicReference");
                                        put("name", "value");
                                        put("descriptor", "Ljava/lang/Object;");
                                    }
                                })
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }
}
