package org.mpisws.jmc.api.util.concurrent;

import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.JmcRuntimeEvent;

/**
 * The LockSupport class is the replacement for {@link java.util.concurrent.locks.LockSupport}
 * class.
 */
public class JmcLockSupport {

    /**
     * Park the current thread.
     *
     * <p>This method calls the parkOperation method of the RuntimeEnvironment class to park the
     * current thread.
     */
    public static void park() {
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.PARK_EVENT)
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
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.UNPARK_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }
}
