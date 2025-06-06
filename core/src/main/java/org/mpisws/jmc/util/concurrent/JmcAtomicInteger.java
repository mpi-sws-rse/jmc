package org.mpisws.jmc.util.concurrent;

import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.RuntimeEvent;

import java.util.HashMap;

public class JmcAtomicInteger {

    public int value;
    public JmcReentrantLock lock = new JmcReentrantLock();

    public JmcAtomicInteger(int initialValue) {
        value = initialValue;
        writeOp(initialValue);
    }

    private void writeOp(int newValue) {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .params(
                                new HashMap<>() {
                                    {
                                        put("newValue", newValue);
                                        put("owner", "org/mpisws/jmc/util/concurrent/AtomicInteger");
                                        put("name", "value");
                                        put("descriptor", "I");
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
                                        put("owner", "org/mpisws/jmc/util/concurrent/AtomicInteger");
                                        put("name", "value");
                                        put("descriptor", "I");
                                    }
                                })
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }

    public JmcAtomicInteger() {
        value = 0;
        writeOp(0);
    }

    public int get() {
        try {
            lock.lock();
            int val = value;
            readOp();
            return val;
        } finally {
            lock.unlock();
        }
    }

    public void set(int newValue) {
        try {
            lock.lock();
            value = newValue;
            writeOp(newValue);
        } finally {
            lock.unlock();
        }
    }

    public boolean compareAndSet(int expectedValue, int newValue) {
        lock.lock();
        try {
            int val = value;
            readOp();
            if (val == expectedValue) {
                value = newValue;
                writeOp(newValue);
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public int getAndIncrement() {
        lock.lock();
        try {
            int result = value;
            readOp();
            value = result + 1;
            writeOp(result + 1);
            return result;
        } finally {
            lock.unlock();
        }
    }
}
