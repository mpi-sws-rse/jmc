package org.mpisws.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Encapsulates all the operations related to Thread objects used by the runtime Except the
 * SchedulerThread The encapsulation ensures no memory leak when creating many threads.
 */
public class ThreadManager {

    /**
     * The state of each thread managed by the @RuntimeEnvironment is represented by one of the
     * following.
     */
    public enum ThreadState {
        RUNNING,
        BLOCKED,
        CREATED,
        TERMINATED,
    }

    /** Stores a set of custom IDs used by the Runtime. */
    private Long idCounter;
    private final Object idCounterLock = new Object();

    /** Stores the state of each thread. */
    private final Map<Long, ThreadState> threadStates;

    /** Stores the future of blocked threads. */
    private final Map<Long, CompletableFuture<Boolean>> threadFutures;

    /**
     * Returns the next thread ID to be assigned.
     *
     * @return the next thread ID to be assigned
     */
    private Long nextThreadId() {
        synchronized (idCounterLock) {
            return idCounter++;
        }
    }

    /**
     * Constructs a new ThreadManager object.
     */
    public ThreadManager() {
        this.idCounter = 1L;
        this.threadStates = new ConcurrentHashMap<>();
        this.threadFutures = new ConcurrentHashMap<>();
    }

    /**
     * Resets the ThreadManager object.
     */
    public void reset() {
        idCounter = 1L;
        threadStates.clear();
        for (CompletableFuture<Boolean> future : threadFutures.values()) {
            future.complete(true);
        }
    }

    /**
     * Adds a new thread to the ThreadManager object.
     * The thread is assigned the next available thread ID and a default name "Thread-ID".
     *
     * @return the ID of the thread
     */
    public Long addNextThread() {
        Long customThreadId = nextThreadId();
        threadStates.put(customThreadId, ThreadState.CREATED);
        return customThreadId;
    }

    /**
     * Pauses the thread with the specified custom ID. A new future is created and stored in the
     * {@link ThreadManager#threadFutures} map. If the thread is already paused, a {@link ThreadAlreadyPaused}
     * exception is thrown.
     *
     * @param threadId the custom ID of the thread
     * @return a future that completes when the thread is resumed
     * @throws ThreadAlreadyPaused if the thread with the specified custom ID is already paused
     */
    public CompletableFuture<Boolean> pause(Long threadId) throws ThreadAlreadyPaused {
        if (threadFutures.containsKey(threadId)) {
            throw new ThreadAlreadyPaused();
        }
        threadStates.put(threadId, ThreadState.BLOCKED);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        threadFutures.put(threadId, future);
        return future;
    }

    /**
     * Resumes the thread with the specified custom ID. The future associated with the thread is completed.
     *
     * @param threadId the custom ID of the thread
     * @throws ThreadNotExists if the thread with the specified custom ID does not exist
     */
    public void resume(Long threadId) throws ThreadNotExists {
        CompletableFuture<Boolean> future = threadFutures.get(threadId);
        if (future == null) {
            throw new ThreadNotExists(threadId);
        }
        future.complete(true);
        threadFutures.remove(threadId);
        threadStates.put(threadId, ThreadState.RUNNING);
    }


    /**
     * Return the size of the thread pool.
     *
     * @return the size of the thread pool
     */
    public int size() {
        return threadStates.size();
    }

    /**
     * Update the state of the thread with the specified custom ID.
     *
     * @param threadId the custom ID of the thread
     * @param state the new state of the thread
     */
    public void markStatus(Long threadId, ThreadState state) {
        threadStates.put(threadId, state);
    }

    /**
     * Return the state of the thread with the specified custom ID.
     *
     * @param threadId the custom ID of the thread
     * @return the state of the thread
     */
    public ThreadState getStatus(Long threadId) {
        return threadStates.get(threadId);
    }

    /**
     * Return all the threads with the specified state.
     *
     * @param state the state of the threads to find
     * @return a list of threads with the specified state
     */
    public List<Long> findThreadsWithStatus(ThreadState state) {
        List<Long> result = new ArrayList<>();
        for (Map.Entry<Long, ThreadState> entry : threadStates.entrySet()) {
            if (entry.getValue() == state) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Return custom IDs of all the threads.
     *
     * @return a list of custom IDs of all the threads
     */
    public List<Long> getActiveThreads() {
        ArrayList<Long> result = new ArrayList<>();
        for (Map.Entry<Long, ThreadState> threadStateEntry : threadStates.entrySet()) {
            if (threadStateEntry.getValue() != ThreadState.TERMINATED) {
                result.add(threadStateEntry.getKey());
            }
        }
        return result;
    }


    /**
     * Return true if the thread with the specified system thread ID is in the thread pool and with status
     * provided.
     *
     * @param threadId the custom ID of the thread
     * @param state the state of the thread
     * @return true if the thread exists with status
     */
    public boolean isThreadOfStatus(Long threadId, ThreadState state) {
        if (!threadStates.containsKey(threadId)) {
            return false;
        }
        return threadStates.get(threadId) == state;
    }

    /**
     * Wait for the thread with the specified custom ID to complete.
     *
     * @param threadId the custom ID of the thread
     */
    public void wait(Long threadId) {
        CompletableFuture<Boolean> future = threadFutures.get(threadId);
        if (future == null) {
            return;
        }
        future.join();
    }
}
