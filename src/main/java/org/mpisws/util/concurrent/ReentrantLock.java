package org.mpisws.util.concurrent;

import org.mpisws.runtime.RuntimeEnvironment;

public class ReentrantLock {

    public void lock() throws JMCInterruptException {
        RuntimeEnvironment.acquireLockReq(this, Thread.currentThread());
        RuntimeEnvironment.acquiredLock(this, Thread.currentThread());
    }

    public void unlock() {
        RuntimeEnvironment.releaseLockReq(this, Thread.currentThread());
        RuntimeEnvironment.releasedLock(this, Thread.currentThread());
        RuntimeEnvironment.waitRequest(Thread.currentThread());
    }
}
