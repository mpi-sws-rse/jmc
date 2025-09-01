package org.mpi_sws.jmc.strategies.tracker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class TrackWaitNotify implements Tracker {

    private static final Logger LOGGER = LogManager.getLogger(TrackWaitNotify.class);

    private Set<Long> activeTasks;
    private Set<Long> trackedTasks;
    private HashMap<Integer, Set<Long>> waitingTasks;
    private HashMap<Integer, Set<Long>> availableTasks;

    public TrackWaitNotify() {
        this.activeTasks = new HashSet<>();
        this.trackedTasks = new HashSet<>();
        this.waitingTasks = new HashMap<>();
        this.availableTasks = new HashMap<>();
    }


    @Override
    public Set<Long> updateEvent(JmcRuntimeEvent event) {
        this.trackedTasks.add(event.getTaskId());
        switch (event.getType()) {
            case WAIT_EVENT -> {
                int objectId = event.getParam("object").hashCode();
                Set<Long> waitingList = this.waitingTasks.computeIfAbsent(objectId, k -> new HashSet<>());
                this.activeTasks.remove(event.getTaskId());
            }
            case WAKEUP_EVENT -> {
                // TODO: Recheck this
                int objectId = event.getParam("object").hashCode();
                Set<Long> waitingList = this.waitingTasks.computeIfAbsent(objectId, k -> new HashSet<>());
                Set<Long> availableList = this.availableTasks.computeIfAbsent(objectId, k -> new HashSet<>());
                if (!availableList.isEmpty()) {
                    Long taskId = waitingList.iterator().next();
                    waitingList.remove(taskId);
                    this.activeTasks.add(taskId);
                } else {
                    availableList.add(event.getTaskId());
                }
            }
        }
    }

    @Override
    public void reset() {

    }
}
