package org.mpi_sws.jmc.api.util.concurrent;

import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;

/**
 * The LockSupport class is the replacement for {@link java.util.concurrent.locks.LockSupport}
 * class.
 */
public class JmcLockSupport {

    /**
     * Parks the current thread (replacement for {@code LockSupport.park()}).
     *
     * <p>Reports a {@code PARK_EVENT} for the current task and yields, letting the runtime block it
     * until a corresponding unpark.
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
     * Unparks a thread (replacement for {@code LockSupport.unpark(thread)}).
     *
     * <p>Reports an {@code UNPARK_EVENT} and yields. (The runtime resolves the unpark from the event;
     * the {@code thread} argument is currently not read.)
     *
     * @param thread the thread to unpark
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
