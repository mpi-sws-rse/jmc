package org.mpi_sws.jmc.api.util.concurrent;

import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils;

public class JmcAtomicLong {

    private long value;
    private final JmcReentrantLock lock;

    public JmcAtomicLong(long initialValue) {
        JmcRuntimeUtils.writeEventWithoutYield(
                this,
                initialValue,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                "value",
                "J"
        );
        value = initialValue;
        JmcRuntime.yield();
        JmcReentrantLock lock = new JmcReentrantLock();
        JmcRuntimeUtils.writeEventWithoutYield(
                this,
                lock,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                "lock",
                "Lorg/mpi_sws/jmc/api/util/concurrent/JmcReentrantLock;"
        );
        this.lock = lock;
        JmcRuntime.yield();
    }

    public JmcAtomicLong() {
        this(0L);
    }

    public long get() {
        JmcRuntimeUtils.readEventWithoutYield(
                this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J");
        long out = value;
        JmcRuntime.yield();
        return out;
    }

    public void set(long newValue) {
        JmcRuntimeUtils.writeEventWithoutYield(
                this,
                newValue,
                "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                "value",
                "J");
        value = newValue;
        JmcRuntime.yield();
    }

    public boolean compareAndSet(long expect, long update) {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J");
            long currentValue = value;
            JmcRuntime.yield();
            if (currentValue == expect) {
                JmcRuntimeUtils.writeEventWithoutYield(
                        this,
                        update,
                        "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                        "value",
                        "J");
                value = update;
                JmcRuntime.yield();
                return true;
            } else {
                return false;
            }
        } finally {
            lock.unlock();
        }
    }

    public long getAndIncrement() {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J");
            long result = value;
            JmcRuntime.yield();

            JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    result + 1,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                    "value",
                    "J");
            value = result + 1;
            JmcRuntime.yield();
            return result;
        } finally {
            lock.unlock();
        }
    }

    public long getAndSet(long newValue) {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J");
            long oldValue = value;
            JmcRuntime.yield();

            JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    newValue,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                    "value",
                    "J");
            value = newValue;
            JmcRuntime.yield();
            return oldValue;
        } finally {
            lock.unlock();
        }
    }

    public long addAndGet(long delta) {
        lock.lock();
        try {
            JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    value + delta,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                    "value",
                    "J");
            value = value + delta;
            JmcRuntime.yield();

            JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J");
            long result = value;
            JmcRuntime.yield();
            return result;
        } finally {
            lock.unlock();
        }
    }

    public long getAndAdd(long delta) {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J");
            long result = value;
            JmcRuntime.yield();

            JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    result + delta,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                    "value",
                    "J");
            value = result + delta;
            JmcRuntime.yield();
            return result;
        } finally {
            lock.unlock();
        }
    }

    public long incrementAndGet() {
        lock.lock();
        try {
            JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    value + 1,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                    "value",
                    "J");
            value = value + 1;
            JmcRuntime.yield();

            JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J");
            long result = value;
            JmcRuntime.yield();
            return result;
        } finally {
            lock.unlock();
        }
    }

    public long getAndDecrement() {
        lock.lock();
        try {
            JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J");
            long result = value;
            JmcRuntime.yield();

            JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    result - 1,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                    "value",
                    "J");
            value = result - 1;
            JmcRuntime.yield();
            return result;
        } finally {
            lock.unlock();
        }
    }

    public long decrementAndGet() {
        lock.lock();
        try {
            JmcRuntimeUtils.writeEventWithoutYield(
                    this,
                    value - 1,
                    "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong",
                    "value",
                    "J");
            value = value - 1;
            JmcRuntime.yield();

            JmcRuntimeUtils.readEventWithoutYield(
                    this, "org/mpi_sws/jmc/api/util/concurrent/JmcAtomicLong", "value", "J");
            long result = value;
            JmcRuntime.yield();
            return result;
        } finally {
            lock.unlock();
        }
    }
}
