package org.mpisws.util.concurrent;

import org.mpisws.runtime.JmcRuntime;
import org.mpisws.runtime.RuntimeEvent;
import org.mpisws.runtime.RuntimeEventType;

import java.util.HashMap;

public class AtomicInteger {

    public int value;
    public ReentrantLock lock = new ReentrantLock();

    public AtomicInteger(int initialValue) {
        value = initialValue;
        writeOp(initialValue);
    }

    private void writeOp(int newValue) {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .params(
                                new HashMap<>() {
                                    {
                                        put("newValue", newValue);
                                        put("owner", "org/mpisws/util/concurrent/AtomicInteger");
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
                        .type(RuntimeEventType.READ_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .params(
                                new HashMap<>() {
                                    {
                                        put("owner", "org/mpisws/util/concurrent/AtomicInteger");
                                        put("name", "value");
                                        put("descriptor", "I");
                                    }
                                })
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }

    public AtomicInteger() {
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

    public boolean compareAndSet(int expectedValue, int newValue) throws JMCInterruptException {
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

    public int getAndIncrement() throws JMCInterruptException {
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
