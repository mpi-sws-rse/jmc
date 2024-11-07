package org.mpisws.util.concurrent;

import org.mpisws.runtime.RuntimeEnvironment;

/** The LockSupport class is the replacement for java.util.concurrent.locks.LockSupport class. */
public class LockSupport {

    /**
     * Park the current thread.
     *
     * <p>This method calls the parkOperation method of the RuntimeEnvironment class to park the
     * current thread.
     */
    public static void park() {
        RuntimeEnvironment.parkOperation(Thread.currentThread());
        // Thread Parked
        RuntimeEnvironment.waitRequest(Thread.currentThread());
    }

    /**
     * Unpark the given thread.
     *
     * <p>This method calls the unparkOperation method of the RuntimeEnvironment class to unpark the
     * given thread.
     *
     * @param thread
     */
    public static void unpark(Thread thread) {
        RuntimeEnvironment.unparkOperation(Thread.currentThread(), thread);
        // Thread Unparked
        RuntimeEnvironment.waitRequest(Thread.currentThread());
    }
}
