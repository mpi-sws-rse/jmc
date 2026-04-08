// Create new file: jmc/core/src/main/java/org/mpi_sws/jmc/strategies/tracker/TrackExecutors.java

package org.mpi_sws.jmc.strategies.tracker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.api.util.concurrent.JmcExecutorService;
import org.mpi_sws.jmc.api.util.concurrent.JmcScheduledExecutorService;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks ExecutorService instances to ensure proper shutdown and prevent memory leaks.
 * Prioritizes tasks related to executor shutdown when executors are not properly closed.
 */
public class TrackExecutors implements Tracker {

    private static final Logger LOGGER = LogManager.getLogger(TrackExecutors.class);

    /**
     * Set of registered executors that need tracking.
     */
    private final Set<ExecutorService> registeredExecutors;



    /**
     * All active tasks.
     */
    private final Set<Long> activeTasks;

    public TrackExecutors() {
        this.registeredExecutors = ConcurrentHashMap.newKeySet();
        this.activeTasks = ConcurrentHashMap.newKeySet();
    }

    @Override
    public Set<Long> updateEvent(JmcRuntimeEvent event) {
        Long taskId = event.getTaskId();
        if (taskId == null) {
            return getActiveTasks();
        }

        activeTasks.add(taskId);

        JmcRuntimeEvent.Type type = event.getType();

        if (type == JmcRuntimeEvent.Type.EXECUTOR_SHUTDOWN_EVENT) {
            handleExecutorEvent(event);
        }


        return getActiveTasks();
    }

    private void handleExecutorEvent(JmcRuntimeEvent event) {
        String action = event.getParam("action");
        ExecutorService executor = event.getParam("executor");

        if ("register".equals(action)) {
            registeredExecutors.add(executor);
            LOGGER.debug("Registered executor: {}", executor);
        }
    }

    /**
     * Checks all registered executors and shuts down any that are not already shutdown.
     * This should be called at the end of each iteration.
     */
    public void shutdownExecutors() {
        for (ExecutorService executor : registeredExecutors) {
            if (executor instanceof JmcExecutorService jmcExecutor) {
                if (!jmcExecutor.isShutdown()) {
                    LOGGER.debug("Executor not shutdown, forcing shutdown: {}", executor);
                    jmcExecutor.shutdown();
                }
            }
            if (executor instanceof JmcScheduledExecutorService jmcExecutor) {
                if (!jmcExecutor.isShutdown()) {
                    LOGGER.debug("Scheduled Executor not shutdown, forcing shutdown: {}", executor);
                    jmcExecutor.shutdown();
                }
            }

        }
    }

    private Set<Long> getActiveTasks() {
        return new HashSet<>(activeTasks);
    }

    @Override
    public void reset() {
        // Shutdown any executors that weren't properly shutdown
        shutdownExecutors();

        activeTasks.clear();
        registeredExecutors.clear();
    }
}
