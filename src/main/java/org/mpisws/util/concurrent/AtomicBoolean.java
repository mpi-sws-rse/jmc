package org.mpisws.util.concurrent;

import org.mpisws.runtime.JmcRuntime;
import org.mpisws.runtime.RuntimeEvent;
import org.mpisws.runtime.RuntimeEventType;

import java.util.HashMap;

public class AtomicBoolean {

    public boolean value;
    public ReentrantLock lock = new ReentrantLock();

    public AtomicBoolean(boolean initialValue) {
        writeOp(initialValue);
        value = initialValue;
    }

    public AtomicBoolean() {
        writeOp(false);
        value = false;
    }

    public boolean get() {
        try {
            lock.lock();
        } catch (RuntimeException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
        readOp();
        return value;
    }

    public void set(boolean newValue) {
        writeOp(newValue);
        value = newValue;
    }

    public boolean compareAndSet(boolean expectedValue, boolean newValue)
            throws JMCInterruptException {
        lock.lock();
        try {
            readOp();
            if (value == expectedValue) {
                writeOp(newValue);
                value = newValue;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    private void writeOp(boolean newValue) {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .params(
                                new HashMap<>() {
                                    {
                                        put("newValue", newValue);
                                        put("owner", "org/mpisws/util/concurrent/AtomicBoolean");
                                        put("name", "value");
                                        put("descriptor", "Z");
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
                        .taskId(JmcRuntime.currentTask())
                        .params(
                                new HashMap<>() {
                                    {
                                        put("owner", "org/mpisws/util/concurrent/AtomicBoolean");
                                        put("name", "value");
                                        put("descriptor", "Z");
                                    }
                                })
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }
}
