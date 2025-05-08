package org.mpisws.jmc.util.concurrent;

import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.RuntimeEvent;

import java.util.HashMap;

public class JmcAtomicReference<V> {

    public V value;

    JmcReentrantLock lock = new JmcReentrantLock();

    public JmcAtomicReference(V initialValue) {
        writeOp(initialValue);
        value = initialValue;
    }

    public boolean compareAndSet(V expectedReference, V newReference) {
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
                        .type(RuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .params(
                                new HashMap<>() {
                                    {
                                        put("newValue", newValue);
                                        put("owner", "org/mpisws/jmc/util/concurrent/AtomicReference");
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
                        .type(RuntimeEvent.Type.READ_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .params(
                                new HashMap<>() {
                                    {
                                        put("owner", "org/mpisws/jmc/util/concurrent/AtomicReference");
                                        put("name", "value");
                                        put("descriptor", "Ljava/lang/Object;");
                                    }
                                })
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }
}
