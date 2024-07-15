package org.mpisws.util.concurrent;

import org.mpisws.runtime.RuntimeEnvironment;

public class LockSupport {

    public static void park() {
        RuntimeEnvironment.parkOperation(Thread.currentThread());
        // Thread Parked
        RuntimeEnvironment.waitRequest(Thread.currentThread());
    }

    public static void unpark(Thread thread) {
        RuntimeEnvironment.unparkOperation(Thread.currentThread(), thread);
        // Thread Unparked
        RuntimeEnvironment.waitRequest(Thread.currentThread());
    }
}
