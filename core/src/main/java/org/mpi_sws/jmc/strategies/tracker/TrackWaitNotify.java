package org.mpi_sws.jmc.strategies.tracker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.runtime.HaltCheckerException;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.mpi_sws.jmc.api.JmcObject.handleHashCode;

public class TrackWaitNotify extends TrackLocks {

    private static final Logger LOGGER = LogManager.getLogger(TrackWaitNotify.class);

    private final Set<Long> activeTasks;
    private final Set<Long> trackedTasks;
    private final HashMap<Integer, Set<Long>> waitingTasks;
    private final HashMap<Integer, Set<Long>> availableTasks;

    public TrackWaitNotify() {
        this.activeTasks = new HashSet<>();
        this.trackedTasks = new HashSet<>();
        this.waitingTasks = new HashMap<>();
        this.availableTasks = new HashMap<>();
    }

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

    @Override
    public void reset() {
        super.reset();
        this.activeTasks.clear();
        this.trackedTasks.clear();
        this.waitingTasks.clear();
        this.availableTasks.clear();
    }
}
