package org.mpisws.jmc.api.util.concurrent;

import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.JmcRuntimeUtils;

/**
 * A redefinition of {@link java.util.concurrent.atomic.AtomicInteger} for JMC model checking. This
 * class provides atomic operations on an integer value, ensuring thread safety through the use of a
 * reentrant lock.
 */
public class JmcAtomicInteger {

    private int value;
    private final JmcReentrantLock lock;

    /**
     * Constructs a new JmcAtomicInteger with the specified initial value.
     *
     * @param initialValue the initial value of the atomic integer
     */
    public JmcAtomicInteger(int initialValue) {
        JmcRuntimeUtils.writeEventWithoutYield(
                this,
                initialValue,
                "org/mpisws/jmc/api/util/concurrent/JmcAtomicInteger",
                "value",
                "I");
        value = initialValue;
        JmcRuntime.yield();
        JmcReentrantLock lock = new JmcReentrantLock();
        JmcRuntimeUtils.writeEventWithoutYield(
                this,
                lock,
                "org/mpisws/jmc/api/util/concurrent/JmcAtomicInteger",
                "lock",
                "Lorg/mpisws/jmc/api/util/concurrent/JmcReentrantLock;");
        this.lock = lock;
        JmcRuntime.yield();
    }

    /** Constructs a new JmcAtomicInteger with an initial value of 0. */
    public JmcAtomicInteger() {
        this(0);
    }

    /**
     * Returns the current value of this atomic integer. Invokes a read event to the JMC runtime.
     *
     * @return the current value
     */
    public int get() {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpisws/jmc/api/util/concurrent/JmcAtomicInteger", "value", "I");
            int out = value;
            JmcRuntime.yield();
            return out;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the value of this atomic integer to the given value. Invokes a write event to the JMC
     * runtime.
     *
     * @param newValue the new value to set
     */
    public void set(int newValue) {
        lock.lock();
        try {
            JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    newValue,
                    "org/mpisws/jmc/api/util/concurrent/JmcAtomicInteger",
                    "value",
                    "I");
            value = newValue;
            JmcRuntime.yield();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically sets the value to the given updated value if the current value is equal to the
     * expected value. Invokes a read followed by a write event to the JMC runtime.
     *
     * @param expectedValue the expected value
     * @param newValue the new value to set if the current value equals the expected value
     * @return true if successful, false otherwise
     */
    public boolean compareAndSet(int expectedValue, int newValue) {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpisws/jmc/api/util/concurrent/JmcAtomicInteger", "value", "I");
            if (value == expectedValue) {
                JmcRuntime.yield();

                JmcRuntimeUtils.writeEventWithoutYield(
                        this,
                        newValue,
                        "org/mpisws/jmc/api/util/concurrent/JmcAtomicInteger",
                        "value",
                        "I");
                value = newValue;
                JmcRuntime.yield();
                return true;
            }
            JmcRuntime.yield();
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically increments the current value by 1 and returns the previous value. Invokes a read
     * followed by a write event to the JMC runtime.
     *
     * @return the previous value before incrementing
     */
    public int getAndIncrement() {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpisws/jmc/api/util/concurrent/JmcAtomicInteger", "value", "I");
            int result = value;
            JmcRuntime.yield();

            JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    result + 1,
                    "org/mpisws/jmc/api/util/concurrent/JmcAtomicInteger",
                    "value",
                    "I");
            value = result + 1;
            JmcRuntime.yield();
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically sets the value to the given new value and returns the previous value. Invokes a
     * read followed by a write event to the JMC runtime.
     *
     * @param newValue the new value to set
     * @return the previous value before setting the new value
     */
    public int getAndSet(int newValue) {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpisws/jmc/api/util/concurrent/JmcAtomicInteger", "value", "I");
            int oldValue = value;
            JmcRuntime.yield();

            JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    newValue,
                    "org/mpisws/jmc/api/util/concurrent/JmcAtomicInteger",
                    "value",
                    "I");
            value = newValue;
            JmcRuntime.yield();
            return oldValue;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically adds the given delta to the current value and returns the updated value. Invokes a
     * read followed by a write event to the JMC runtime.
     *
     * @param delta the value to add
     * @return the updated value after addition
     */
    public int addAndGet(int delta) {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpisws/jmc/api/util/concurrent/JmcAtomicInteger", "value", "I");
            int result = value;
            JmcRuntime.yield();

            JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    result + delta,
                    "org/mpisws/jmc/api/util/concurrent/JmcAtomicInteger",
                    "value",
                    "I");
            value = result + delta;
            JmcRuntime.yield();
            return value;
        } finally {
            lock.unlock();
        }
    }
}
