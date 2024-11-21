package org.mpisws.util.concurrent;

import java.util.HashMap;
import org.mpisws.runtime.JmcRuntime;
import org.mpisws.runtime.RuntimeEvent;
import org.mpisws.runtime.RuntimeEventType;

public class AtomicInteger {

    public int value;
    public ReentrantLock lock = new ReentrantLock();

    public AtomicInteger(int initialValue) {
        write(initialValue);
    }

    private void write(int newValue) {
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
        value = newValue;
    }

    private int read() {
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
        return value;
    }

    public AtomicInteger() {
        write(0);
    }

    public int get() {
        return read();
    }

    public void set(int newValue) {
        write(newValue);
    }

    public boolean compareAndSet(int expectedValue, int newValue) throws JMCInterruptException {
        lock.lock();
        try {
            read();
            if (value == expectedValue) {
                write(newValue);
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
            int result = read();
            write(result + 1);
            return result;
        } finally {
            lock.unlock();
        }
    }
}
