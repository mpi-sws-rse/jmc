// Create new file: jmc/core/src/main/java/org/mpi_sws/jmc/strategies/tracker/TrackStaticInit.java

package org.mpi_sws.jmc.strategies.tracker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks static initialization events to ensure proper synchronization
 * of static initializers across iterations.
 */
public class TrackStaticInit implements Tracker {

    /** Logger for this tracker. */
    private static final Logger LOGGER = LogManager.getLogger(TrackStaticInit.class);

    /**
     * Tracks which task is currently executing static initialization.
     * Only one task can execute static init at a time (mimics JVM's synchronized <clinit>).
     */
    private final Map<Long, Integer> currentStaticInitTask = new ConcurrentHashMap<>();

    /**
     * Tasks waiting to execute static initialization.
     */

    /**
     * All active tasks.
     */
    private final Set<Long> activeTasks;

    /** Constructs a new static-init tracker with empty state. */
    public TrackStaticInit() {
        this.activeTasks = ConcurrentHashMap.newKeySet();
    }

    /**
     * Tracks static-initialization events to enforce JVM-like single-threaded {@code <clinit>}.
     *
     * <p>{@code START_STATIC_INIT_EVENT} marks the task as performing static init (with a
     * re-entrancy count); {@code END_STATIC_INIT_EVENT} decrements/clears it. While any task is
     * performing static init, only that task is reported as runnable; otherwise all active tasks are
     * returned.
     *
     * @param event the event to process
     * @return the runnable task set (the single initializing task, or all active tasks)
     */
    @Override
    public Set<Long> updateEvent(JmcRuntimeEvent event) {
        Long taskId = event.getTaskId();
        if (taskId == null) {
            return getActiveTasks();
        }

        activeTasks.add(taskId);

        JmcRuntimeEvent.Type type = event.getType();

        if (type == JmcRuntimeEvent.Type.START_STATIC_INIT_EVENT) {
            if (currentStaticInitTask.containsKey(taskId)) {
                int count = currentStaticInitTask.get(taskId);
                count++;
                currentStaticInitTask.put(taskId, count);
            } else {
                currentStaticInitTask.put(taskId, 1);
            }
        } else if (type == JmcRuntimeEvent.Type.END_STATIC_INIT_EVENT) {
            if (!currentStaticInitTask.containsKey(taskId)) {
                // TODO throw an error
            }

            int count = currentStaticInitTask.get(taskId);
            if (count == 1) {
                currentStaticInitTask.remove(taskId);
            } else {
                currentStaticInitTask.put(taskId, count - 1);
            }
        }
        if (currentStaticInitTask.isEmpty()) {
            return getActiveTasks();
        } else {
            Set keySet = currentStaticInitTask.keySet();
            if (keySet.size() != 1) {
                // TODO : Throw and error
            }
            return new HashSet<>(keySet);
        }

    }


    /**
     * Returns a snapshot copy of all active tasks.
     *
     * @return a copy of the active task set
     */
    private Set<Long> getActiveTasks() {
        return new HashSet<>(activeTasks);
    }

    /** Clears all tracked static-initialization state. */
    @Override
    public void reset() {
        activeTasks.clear();
        //waitingForStaticInit.clear();
        currentStaticInitTask.clear();
    }
}
