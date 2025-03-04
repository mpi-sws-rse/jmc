package org.mpisws.jmc.util.concurrent;

import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.RuntimeEvent;
import org.mpisws.jmc.runtime.RuntimeEventType;

/**
 * A reentrant lock that can be used to synchronize access to shared resources.
 *
 * <p>Replacement for java.util.concurrent.ReentrantLock</p>
 * <p>Yields control to the runtime for lock and unlock.
 */
public class JmcReentrantLock {

    /**
     * Acquires the lock.
     */
    public void lock() {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.LOCK_ACQUIRE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("owner", "org/mpisws/jmc/util/concurrent/ReentrantLock")
                        .param("lock", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
        event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.LOCK_ACQUIRED_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("owner", "org/mpisws/jmc/util/concurrent/ReentrantLock")
                        .param("lock", this)
                        .build();
        JmcRuntime.updateEvent(event);
    }

    /**
     * Releases the lock.
     */
    public void unlock() {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.LOCK_RELEASE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("owner", "org/mpisws/jmc/util/concurrent/ReentrantLock")
                        .param("lock", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }
}
