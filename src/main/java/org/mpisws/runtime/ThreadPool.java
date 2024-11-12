package org.mpisws.runtime;

import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates all the operations related to Thread objects used by the runtime Except the
 * SchedulerThread The encapsulation ensures no memory leak when creating many threads
 */
public class ThreadPool {

    /** Contains references to the Thread objects with actual thread IDs */
    private final Map<Long, Thread> threads;

    /** Stores a mapping from custom IDs to the actual thread IDs */
    private final Map<Long, Long> idMap;

    public ThreadPool() {
        this.threads = new HashMap<>();
        this.idMap = new HashMap<>();
    }

    public void reset() {
        threads.clear();
        idMap.clear();
    }

    public Long add(Thread thread, Long customId, String customName) {
        Long threadId = thread.getId();
        threads.put(threadId, thread);
        thread.setName(customName);
        idMap.put(customId, threadId);
        return threadId;
    }

    public Thread get(Long customId) throws ThreadNotExists {
        Long threadId = idMap.get(customId);
        if (threadId == null) {
            throw new ThreadNotExists(customId);
        }
        return threads.get(customId);
    }
}
