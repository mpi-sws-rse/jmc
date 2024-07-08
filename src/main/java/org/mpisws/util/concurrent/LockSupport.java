package org.mpisws.util.concurrent;

import org.mpisws.runtime.RuntimeEnvironment;

public class LockSupport {

    public static void park() {
        //Thread t = Thread.currentThread();
        System.out.println("[Debugging Message] : new park() method is called!");
        RuntimeEnvironment.parkOperation(Thread.currentThread());
    }

    public static void unpark(Thread thread) {
        System.out.println("[Debugging Message] : new unpark() method is called!");
        System.out.println("[Debugging Message] : The thread is " + thread.getName() + ":" + thread.getId());
        RuntimeEnvironment.unparkOperation(Thread.currentThread(), thread);
    }
}
