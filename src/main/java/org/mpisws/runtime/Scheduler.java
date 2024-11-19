package org.mpisws.runtime;

import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.strategies.SchedulingStrategy;

/**
 * The scheduler is responsible for managing the execution of threads.
 *
 * <p>The scheduler uses the strategy to decide which thread to Schedule.
 * To do so, it instantiates a separate SchedulerThread that is blocked whenever other
 * threads are running and unblocked only when it need to pick the next thread to schedule.</p>
 *
 * <p>To interact with the scheduler, invoke the {@link Scheduler#yield()} which will defer control
 * the scheduler thread.</p>
 */
public class Scheduler {

    private static final Logger LOGGER = LogManager.getLogger(Scheduler.class.getName());

    /** The thread manager used to manage the thread states. */
    private ThreadManager threadManager;

    /** The scheduling strategy used to decide which thread to schedule. */
    private final SchedulingStrategy strategy;

    /** The ID of the current thread. Protected by the lock for accesses to read and write*/
    private Long currentThread;
    private final Object currentThreadLock = new Object();

    /** The scheduler thread instance. */
    private final SchedulerThread schedulerThread;

    /**
     * Constructs a new Scheduler object.
     *
     * @param strategy the scheduling strategy
     */
    public Scheduler(SchedulingStrategy strategy) {
        this.strategy = strategy;
        this.schedulerThread = new SchedulerThread(this, strategy);
    }

    /**
     * Initializes the scheduler with the thread manager and the main thread.
     *
     * @param threadManager the thread manager
     * @param mainThreadId the ID of the main thread
     */
    public void init(ThreadManager threadManager, Long mainThreadId) {
        this.threadManager = threadManager;
        this.setCurrentThread(mainThreadId);
    }

    /**
     * Returns the ID of the current thread.
     *
     * @return the ID of the current thread
     */
    public Long currentThread() {
        synchronized (currentThreadLock) {
            return currentThread;
        }
    }

    /**
     * Sets the ID of the current thread.
     *
     * @param threadId the ID of the current thread
     */
    protected void setCurrentThread(Long threadId) {
        synchronized (currentThreadLock) {
            currentThread = threadId;
        }
    }

    /**
     * Schedules the thread with the given ID. The thread is resumed using the ThreadManager.
     * Exits the program if the thread does not exist. (Should never happen)
     *
     * @param threadId the ID of the thread to be scheduled
     */
    protected void scheduleThread(Long threadId) {
        setCurrentThread(threadId);
        try {
            threadManager.resume(threadId);
        } catch (ThreadNotExists e) {
            LOGGER.error("Resuming a non existent thread: {}", e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Updates the event in the scheduling strategy.
     *
     * @param event the event to be updated
     */
    public void updateEvent(RuntimeEvent event) {
        strategy.updateEvent(event);
    }

    /**
     * Pauses the current thread and yields the control to the scheduler.
     *
     * @return a future that completes when the thread is resumed
     * @throws ThreadAlreadyPaused if the current thread is already paused
     */
    public CompletableFuture<Boolean> yield() throws ThreadAlreadyPaused {
        CompletableFuture<Boolean> future;
        synchronized (currentThreadLock) {
            future = threadManager.pause(currentThread);
            currentThread = null;
        }
        // Release the scheduler thread
        schedulerThread.enable();
        return future;
    }

    /**
     * Resets the ThreadManager and the scheduling strategy for a new iteration.
     */
    public void endIteration() {
        strategy.reset();
    }

    /**
     * The SchedulerThread class is responsible for scheduling the threads.
     */
    private static class SchedulerThread extends Thread {

        private static final Logger LOGGER = LogManager.getLogger(SchedulerThread.class.getName());

        /** The scheduler instance. */
        private final Scheduler scheduler;
        /** The scheduling strategy used by the scheduler. */
        private final SchedulingStrategy strategy;

        /** The future that is completed when the scheduler is enabled. Protected by a lock.
         *  Every time the scheduler is enabled a new future is created.
         */
        private CompletableFuture<Boolean> future;
        private final Object futureLock = new Object();

        /**
         * Constructs a new SchedulerThread object.
         *
         * @param scheduler the scheduler instance
         * @param strategy the scheduling strategy
         */
        public SchedulerThread(Scheduler scheduler, SchedulingStrategy strategy) {
            this.scheduler = scheduler;
            this.strategy = strategy;
            this.future = new CompletableFuture<>();
        }

        /**
         * Enables the scheduler.
         */
        public void enable() {
            synchronized (futureLock) {
                future.complete(false);
            }
        }

        /**
         * Shuts down the scheduler.
         */
        public void shutdown() {
            synchronized (futureLock) {
                future.complete(true);
            }
        }

        /**
         * The main loop of the scheduler thread.
         */
        @Override
        public void run() {
            LOGGER.info("Scheduler thread started.");
            while (true) {
                // Wait for the scheduler to be enabled
                CompletableFuture<Boolean> curFuture;
                synchronized (futureLock) {
                    curFuture = future;
                }
                try {
                    Boolean shutdown = curFuture.join();
                    if (shutdown) {
                        break;
                    }
                    Long nextThread = strategy.nextThread();
                    if (nextThread == null) {
                        break;
                    }
                    scheduler.scheduleThread(nextThread);
                } catch (Exception e) {
                    LOGGER.error("Scheduler thread threw an exception: {}", e.getMessage());
                    break;
                } finally {
                    // Reset the future to wait for the next iteration
                    synchronized (futureLock) {
                        future = new CompletableFuture<>();
                    }
                }
            }
            LOGGER.info("Scheduler thread finished.");
        }
    }
}
