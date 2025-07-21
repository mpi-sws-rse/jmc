package org.mpisws.jmc.api.util.concurrent;

import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.JmcRuntimeUtils;

import java.util.concurrent.locks.ReentrantLock;

/**
 * A redefinition of {@link java.util.concurrent.atomic.AtomicBoolean} that communicates with JMC
 * runtime to perform read, write, and compare-and-set operations.
 */
public class JmcAtomicBoolean {

    private boolean value;
    private final JmcReentrantLock lock;

    /**
     * Constructs a new JmcAtomicBoolean with the specified initial value.
     *
     * @param initialValue the initial value of the atomic boolean
     */
    public JmcAtomicBoolean(boolean initialValue) {
        JmcRuntimeUtils.writeEventWithoutYield(
                this,
                initialValue,
                "org/mpisws/jmc/api/util/concurrent/JmcAtomicBoolean",
                "value",
                "Z");
        this.value = initialValue;
        JmcRuntime.yield();
        JmcReentrantLock lock = new JmcReentrantLock();
        JmcRuntimeUtils.writeEventWithoutYield(
                this,
                lock,
                "org/mpisws/jmc/api/util/concurrent/JmcAtomicBoolean",
                "lock",
                "Lorg/mpisws/jmc/api/util/concurrent/JmcReentrantLock;");
        this.lock = lock;
        JmcRuntime.yield();
    }

    /** Constructs a new JmcAtomicBoolean with an initial value of false. */
    public JmcAtomicBoolean() {
        this(false);
    }

    /**
     * Returns the current value of this atomic boolean. Invokes a read event to the JMC runtime.
     *
     * @return the current value
     */
    public boolean get() {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpisws/jmc/api/util/concurrent/JmcAtomicBoolean", "value", "Z");
            boolean out = value;
            JmcRuntime.yield();
            return out;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the value of this atomic boolean to the given value. Invokes a write event to the JMC
     * runtime.
     *
     * @param newValue the new value to set
     */
    public void set(boolean newValue) {
        lock.lock();
        try {
            JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    newValue,
                    "org/mpisws/jmc/api/util/concurrent/JmcAtomicBoolean",
                    "value",
                    "Z");
            value = newValue;
            JmcRuntime.yield();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically sets the value to the given updated value if the current value is equal to the
     * expected value. Invokes a read event followed by a write event to the JMC runtime.
     *
     * @param expectedValue the expected value
     * @param newValue the new value to set if the current value equals the expected value
     * @return true if successful, false otherwise
     */
    public boolean compareAndSet(boolean expectedValue, boolean newValue) {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpisws/jmc/api/util/concurrent/JmcAtomicBoolean", "value", "Z");
            if (value == expectedValue) {
                JmcRuntime.yield();

                JmcRuntimeUtils.writeEventWithoutYield(
                        this,
                        newValue,
                        "org/mpisws/jmc/api/util/concurrent/JmcAtomicBoolean",
                        "value",
                        "Z");
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
}
