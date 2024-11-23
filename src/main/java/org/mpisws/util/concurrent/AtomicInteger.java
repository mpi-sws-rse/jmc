package org.mpisws.util.concurrent;

import org.mpisws.runtime.JmcRuntime;
import org.mpisws.runtime.RuntimeEvent;
import org.mpisws.runtime.RuntimeEventType;

import java.util.HashMap;

public class AtomicInteger {

    public int value;
    public ReentrantLock lock = new ReentrantLock();

    public AtomicInteger(int initialValue) {
        writeOp(initialValue);
        value = initialValue;
    }

    private void writeOp(int newValue) {
        RuntimeEvent event =
                new RuntimeEvent(
                        RuntimeEventType.WRITE_EVENT,
                        JmcRuntime.currentTask(),
                        new HashMap<>() {
                            {
                                put("newValue", newValue);
                                put("owner", "org/mpisws/util/concurrent/AtomicInteger");
                                put("name", "value");
                                put("descriptor", "I");
                            }
                        });
        JmcRuntime.updateEventAndYield(event);
    }

    private void readOp() {
        RuntimeEvent event =
                new RuntimeEvent(
                        RuntimeEventType.READ_EVENT,
                        JmcRuntime.currentTask(),
                        new HashMap<>() {
                            {
                                put("owner", "org/mpisws/util/concurrent/AtomicInteger");
                                put("name", "value");
                                put("descriptor", "I");
                            }
                        });
        JmcRuntime.updateEventAndYield(event);
    }

    public AtomicInteger() {
        writeOp(0);
        value = 0;
    }

    public int get() {
        try {
            lock.lock();
            readOp();
            return value;
        } finally {
            lock.unlock();
        }
    }

    public void set(int newValue) {
        try {
            lock.lock();
            writeOp(newValue);
            value = newValue;
        } finally {
            lock.unlock();
        }
    }

    public boolean compareAndSet(int expectedValue, int newValue) throws JMCInterruptException {
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

    public int getAndIncrement() throws JMCInterruptException {
        lock.lock();
        try {
            readOp();
            int result = value;
            writeOp(result + 1);
            value = result + 1;
            return result;
        } finally {
            lock.unlock();
        }
    }
}
