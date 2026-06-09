package org.mpi_sws.jmc.strategies.tracker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.runtime.HaltCheckerException;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.mpi_sws.jmc.api.JmcObject.handleHashCode;

/**
 * Tracks Java monitor {@code wait}/{@code notify} semantics on top of {@link TrackLocks}.
 *
 * <p>Maintains, per monitor object, a set of tasks parked in {@code wait()} and a set of tasks that
 * have been notified and are eligible to re-acquire the monitor. A waiting task is blocked (and its
 * lock released); {@code notify}/{@code notifyAll} move waiters back toward active. The runnable set
 * it reports is intersected with the lock tracker's runnable set.
 */
public class TrackWaitNotify extends TrackLocks {

    /** Logger used to trace the runnable set after wait/notify processing. */
    private static final Logger LOGGER = LogManager.getLogger(TrackWaitNotify.class);

    /** Tasks considered runnable by this tracker (not parked in a wait set). */
    private final Set<Long> activeTasks;
    /** All tasks this tracker has observed at least one event from. */
    private final Set<Long> trackedTasks;
    /** For each monitor object (by hash code), the tasks currently parked in {@code wait()}. */
    private final HashMap<Integer, Set<Long>> waitingTasks;
    /** For each monitor object (by hash code), the notified tasks eligible to re-acquire it. */
    private final HashMap<Integer, Set<Long>> availableTasks;

    /** Constructs a new wait/notify tracker with empty state. */
    public TrackWaitNotify() {
        this.activeTasks = new HashSet<>();
        this.trackedTasks = new HashSet<>();
        this.waitingTasks = new HashMap<>();
        this.availableTasks = new HashMap<>();
    }

    /**
     * Tracks wait/notify events on top of lock tracking.
     *
     * <p>After delegating to {@link TrackLocks#updateEvent(JmcRuntimeEvent)}: {@code WAIT_EVENT}
     * parks the task on the object's wait set and releases its lock; {@code NOTIFY_EVENT} moves the
     * object's waiters to the available set and back to active; {@code NOTIFY_ALL_EVENT} moves all
     * waiters (and previously available tasks) to active; {@code WAKEUP_EVENT} consumes an available
     * notification (and errors if none is available). The result is intersected with the lock
     * tracker's active set.
     *
     * @param event the event to process
     * @return the set of tasks runnable according to both this tracker and the lock tracker
     */
    @Override
    public Set<Long> updateEvent(JmcRuntimeEvent event) {
        super.updateEvent(event);
        if (!this.trackedTasks.contains(event.getTaskId())) {
            this.activeTasks.add(event.getTaskId());
        }
        this.trackedTasks.add(event.getTaskId());
        switch (event.getType()) {
            case WAIT_EVENT -> {
                // TODO: need validation to ensure that wait is called on an object that is locked
                // by the current thread. If not, throw an exception saying error in wait/notify
                // usage.
                Object object = event.getParam("object");
                int objectId = handleHashCode(object);
                Set<Long> waitingList =
                        this.waitingTasks.computeIfAbsent(objectId, k -> new HashSet<>());
                waitingList.add(event.getTaskId());
                this.waitingTasks.put(objectId, waitingList);
                this.activeTasks.remove(event.getTaskId());
                this.unlock(event.getTaskId(), object);
            }
            case WAKEUP_EVENT -> {
                int objectId = event.getParam("object").hashCode();
                Set<Long> waitingList =
                        this.waitingTasks.computeIfAbsent(objectId, k -> new HashSet<>());
                Set<Long> availableList =
                        this.availableTasks.computeIfAbsent(objectId, k -> new HashSet<>());
                if (availableList.isEmpty()) {
                    throw HaltCheckerException.error("No available tasks to wake up");
                }
                Long taskId = event.getTaskId();
                availableList.remove(taskId);
                waitingList.addAll(availableList);
                this.waitingTasks.put(objectId, waitingList);
                this.availableTasks.put(objectId, new HashSet<>());
                this.activeTasks.removeAll(availableList);
            }
            case NOTIFY_EVENT -> {
                int objectId = event.getParam("object").hashCode();
                Set<Long> waitingList =
                        this.waitingTasks.computeIfAbsent(objectId, k -> new HashSet<>());
                Set<Long> availableList =
                        this.availableTasks.computeIfAbsent(objectId, k -> new HashSet<>());
                availableList.addAll(waitingList);
                this.availableTasks.put(objectId, availableList);
                this.activeTasks.addAll(waitingList);
                waitingList.clear();
                this.waitingTasks.put(objectId, waitingList);
            }
            case NOTIFY_ALL_EVENT -> {
                int objectId = event.getParam("object").hashCode();
                Set<Long> waitingList =
                        this.waitingTasks.computeIfAbsent(objectId, k -> new HashSet<>());
                this.activeTasks.addAll(waitingList);
                waitingList.clear();
                Set<Long> availableList =
                        this.availableTasks.computeIfAbsent(objectId, k -> new HashSet<>());
                this.activeTasks.addAll(availableList);
                this.availableTasks.put(objectId, new HashSet<>());
                this.waitingTasks.put(objectId, waitingList);
            }
        }
        Set<Long> result = new HashSet<>(this.activeTasks);
        result.retainAll(super.getActiveTasks());
        LOGGER.debug("Active tasks after wait/notify: {}", result);
        return result;
    }

    /** Clears all wait/notify state and the underlying lock state. */
    @Override
    public void reset() {
        super.reset();
        this.activeTasks.clear();
        this.trackedTasks.clear();
        this.waitingTasks.clear();
        this.availableTasks.clear();
    }
}
