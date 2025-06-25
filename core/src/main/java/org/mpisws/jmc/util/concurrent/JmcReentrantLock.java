package org.mpisws.jmc.util.concurrent;

import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.JmcRuntimeUtils;
import org.mpisws.jmc.runtime.RuntimeEvent;

import java.util.concurrent.locks.ReentrantLock;

/**
 * A reentrant lock that can be used to synchronize access to shared resources.
 *
 * <p>Replacement for java.util.concurrent.ReentrantLock
 *
 * <p>Yields control to the runtime for lock and unlock.
 */
public class JmcReentrantLock {

    private final int token;
    private final ReentrantLock lock = new ReentrantLock();

    public JmcReentrantLock() {
        JmcRuntimeUtils.writeEventWithoutYield(this, 0, "org/mpisws/jmc/util/concurrent/JmcReentrantLock",
                "token", "I");
        token = 0;
        JmcRuntime.yield();
    }

    /**
     * Acquires the lock.
     */
    public void lock() {
        JmcRuntimeUtils.lockAcquireEvent("org/mpisws/jmc/util/concurrent/JmcReentrantLock",
                "token", token, "I", this);

        lock.lock();

        JmcRuntimeUtils.lockAcquiredEventWithoutYield(this,
                "org/mpisws/jmc/util/concurrent/JmcReentrantLock",
                "token", token, "I", 1);
    }

    /**
     * Releases the lock.
     */
    public void unlock() {
        lock.unlock();

        JmcRuntimeUtils.lockReleaseEvent(this,
                "org/mpisws/jmc/util/concurrent/JmcReentrantLock", "token", token, "I", 0);
    }
}
