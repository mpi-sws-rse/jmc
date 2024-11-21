package org.mpisws.util.concurrent;

import org.mpisws.runtime.JmcRuntime;
import org.mpisws.runtime.RuntimeEvent;
import org.mpisws.runtime.RuntimeEventType;

/** The LockSupport class is the replacement for java.util.concurrent.locks.LockSupport class. */
public class LockSupport {

    /**
     * Park the current thread.
     *
     * <p>This method calls the parkOperation method of the RuntimeEnvironment class to park the
     * current thread.
     */
    public static void park() {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.PARK_EVENT)
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
                        .type(RuntimeEventType.UNPARK_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }
}
