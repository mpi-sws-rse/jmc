package org.mpisws.runtime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.strategies.SchedulingStrategy;

/**
 * The Runtime environment complete with a scheduler and configuration options
 * used by the model checker.
 *
 * <p>Calls to the runtime are made by the instrumented byte code. These calls are used to
 * record events occurring during the execution of threads or allow for scheduling changes.
 * For example, the runtime can be used to record Thread creation and deletion.</p>
 *
 * <p>The runtime is a static class that stores minimal states and delegates calls to the
 * {@link Scheduler} which retains all the state.</p>
 */
public class JmcRuntime {

    private static Logger LOGGER = LogManager.getLogger(JmcRuntime.class);

    public static ThreadManager threadManager = new ThreadManager();

    public static Scheduler scheduler;

    /**
     * Constructs a new JmcRuntime object.
     */
    public static void setup(SchedulingStrategy strategy) {
        scheduler = new Scheduler(strategy);
    }

    /**
     * Initializes the runtime with the main thread for a given iteration.
     *
     * <p>Initializes the scheduler with the main thread and marks it as ready.</p>
     *
     * @param mainThread the System thread ID of the main thread
     */
    public static void initIteration(Long mainThread, int iteration) {
        LOGGER = LogManager.getLogger(JmcRuntime.class.getName() + iteration);
        Long mainThreadId = threadManager.addNextThread(mainThread);
        threadManager.markStatus(mainThreadId, ThreadManager.ThreadState.BLOCKED);

        scheduler.init(threadManager, mainThreadId);
        scheduler.updateEvent(new RuntimeEvent(RuntimeEventType.START_EVENT, mainThreadId));
        JmcRuntime.yield();
    }

    /**
     * Resets the runtime for a new iteration.
     */
    public static void resetIteration() {
        scheduler.endIteration();
        threadManager.reset();
    }

    /**
     * Pauses the current thread that invokes this method and yields the control to the scheduler.
     * The call returns only when the thread that invoked this method is resumed.
     */
    public static void yield() {
        Long currentThread = scheduler.currentThread();
        try {
            scheduler.yield();
        } catch (ThreadAlreadyPaused e) {
            LOGGER.error("Yielding an already paused thread.");
            System.exit(1);
        }
        threadManager.wait(currentThread);
    }

    /**
     * Returns the current thread id.
     *
     * @return the current thread id
     */
    public static Long currentThread() {
        Long currentThread = scheduler.currentThread();
        if (currentThread == null) {
            LOGGER.error("No current thread.");
            System.exit(1);
        }
        return currentThread;
    }

    /**
     * Adds a new event to the runtime.
     *
     * @param event the new thread
     */
    public static void updateEvent(RuntimeEvent event) {
        scheduler.updateEvent(event);
    }

    /**
     * Adds a new event to the runtime and yields the control to the scheduler.
     *
     * @param event the new event
     */
    public static void updateEventAndYield(RuntimeEvent event) {
        updateEvent(event);
        JmcRuntime.yield();
    }

    public static Long addNewThread() {
        return threadManager.addNextThread();
    }


}
