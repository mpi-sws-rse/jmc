package org.mpisws.jmc.util.concurrent;

import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.RuntimeEvent;

/**
 * The LockSupport class is the replacement for java.util.concurrent.locks.LockSupport class.
 */
public class JmcLockSupport {

    /**
     * Park the current thread.
     *
     * <p>This method calls the parkOperation method of the RuntimeEnvironment class to park the
     * current thread.
     */
    public static void park() {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.PARK_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .build();
        JmcRuntime.updateEventAndYield(event);
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
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.UNPARK_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }
}
