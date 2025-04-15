package org.mpisws.jmc.util.concurrent;

import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.RuntimeEvent;

import java.util.HashMap;

public class JmcAtomicBoolean {

    public boolean value;
    public JmcReentrantLock lock = new JmcReentrantLock();

    public JmcAtomicBoolean(boolean initialValue) {
        writeOp(initialValue);
        value = initialValue;
    }

    public JmcAtomicBoolean() {
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
        boolean out = value;
        RuntimeEvent.Builder eventBuilder =
                (new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.READ_EVENT)
                        .taskId(JmcRuntime.currentTask()));
        HashMap<String, Object> eventParams = new HashMap<>();
        eventParams.put("owner", "org/mpisws/jmc/util/concurrent/AtomicBoolean");
        eventParams.put("name", "value");
        eventParams.put("descriptor", "Z");
        JmcRuntime.updateEventAndYield(eventBuilder.params(eventParams).param("instance", this).build());
        return out;
    }

    public void set(boolean newValue) {
        value = newValue;
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .params(
                                new HashMap<>() {
                                    {
                                        put("newValue", newValue);
                                        put("owner", "org/mpisws/jmc/util/concurrent/AtomicBoolean");
                                        put("name", "value");
                                        put("descriptor", "Z");
                                    }
                                })
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }

    public boolean compareAndSet(boolean expectedValue, boolean newValue) {
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
                        .type(RuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .params(
                                new HashMap<>() {
                                    {
                                        put("newValue", newValue);
                                        put("owner", "org/mpisws/jmc/util/concurrent/AtomicBoolean");
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
                        .type(RuntimeEvent.Type.READ_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .params(
                                new HashMap<>() {
                                    {
                                        put("owner", "org/mpisws/jmc/util/concurrent/AtomicBoolean");
                                        put("name", "value");
                                        put("descriptor", "Z");
                                    }
                                })
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }
}
