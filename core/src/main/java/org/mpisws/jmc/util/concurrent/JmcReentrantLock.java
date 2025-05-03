package org.mpisws.jmc.util.concurrent;

import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.RuntimeEvent;

/**
 * A reentrant lock that can be used to synchronize access to shared resources.
 *
 * <p>Replacement for java.util.concurrent.ReentrantLock</p>
 * <p>Yields control to the runtime for lock and unlock.
 */
public class JmcReentrantLock {

    public int token = 0;
    /**
     * Acquires the lock.
     */
    public void lock() {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.LOCK_ACQUIRE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("owner", "org/mpisws/jmc/util/concurrent/ReentrantLock")
                        .param("name", "token")
                        .param("value", token)
                        .param("descriptor", "I")
                        .param("lock", this)
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
        event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.LOCK_ACQUIRED_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("owner", "org/mpisws/jmc/util/concurrent/ReentrantLock")
                        .param("lock", this)
                        .param("name", "token")
                        .param("descriptor", "I")
                        .param("value", token)
                        .param("instance", this)
                        .param("newValue", 1)
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }

    /**
     * Releases the lock.
     */
    public void unlock() {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.LOCK_RELEASE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("owner", "org/mpisws/jmc/util/concurrent/ReentrantLock")
                        .param("name", "token")
                        .param("descriptor", "I")
                        .param("value", token)
                        .param("newValue", 0)
                        .param("lock", this)
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }
}
