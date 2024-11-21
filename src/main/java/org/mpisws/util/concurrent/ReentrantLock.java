package org.mpisws.util.concurrent;

import org.mpisws.runtime.JmcRuntime;

public class ReentrantLock {

    // TODO: need
    public ReentrantLock() {

        JmcRuntime.initLock(this, Thread.currentThread());
        JmcRuntime.waitRequest(Thread.currentThread());
    }

    public void lock() throws JMCInterruptException {
        JmcRuntime.acquireLockReq(this, Thread.currentThread());
        JmcRuntime.acquiredLock(this, Thread.currentThread());
    }

    public void unlock() {
        JmcRuntime.releaseLockReq(this, Thread.currentThread());
        JmcRuntime.releasedLock(this, Thread.currentThread());
        JmcRuntime.waitRequest(Thread.currentThread());
    }
}
