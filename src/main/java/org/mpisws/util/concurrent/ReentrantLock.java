package org.mpisws.util.concurrent;

import org.mpisws.runtime.JmcRuntime;
import org.mpisws.runtime.RuntimeEvent;
import org.mpisws.runtime.RuntimeEventType;

public class ReentrantLock {
    public void lock() throws JMCInterruptException {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.LOCK_ACQUIRE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("owner", "org/mpisws/util/concurrent/ReentrantLock")
                        .param("lock", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }

    public void unlock() {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEventType.LOCK_RELEASE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("owner", "org/mpisws/util/concurrent/ReentrantLock")
                        .param("lock", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }
}
