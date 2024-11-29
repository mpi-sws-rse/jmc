package org.mpisws.strategies.trust;

import org.mpisws.runtime.HaltExecutionException;
import org.mpisws.runtime.HaltTaskException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * Contains the core Trust algorithm implementation. (<a
 * href="https://doi.org/10.1145/3498711"></a>) We implement the iterative version of the algorithm
 * that is presented in detail in the thesis. (<a
 * href="https://kluedo.ub.rptu.de/frontdoor/index/index/docId/7670"></a>)
 *
 * <p>This class contains, in addition to the execution graph and the algorithm, the auxiliary state
 * needed to enforce a specific task scheduling order.
 *
 * <p>Disclaimer!! This algorithm assumes that the programs are deterministic. Meaning, if you run a
 * task, you will receive the same sequence of events.
 */
public class Algo {
    // The sequence of tasks to be scheduled. This is kept in sync with the execution graph that we
    // are currently visiting.
    private List<Long> taskSchedule;
    // The execution graph used by the algorithm.
    private ExecutionGraph theExecutionGraph;
    // The execution graph of the current iteration.
    // Need to figure this out.
    private ExecutionGraph curExecutionGraph;

    // The set of events that we need to backtrack
    private HashMap<Event, Event> backtrackSet;

    /** Creates a new instance of the Trust algorithm. */
    public Algo() {
        this.taskSchedule = new ArrayList<>();
        this.theExecutionGraph = new ExecutionGraph();
        this.backtrackSet = new HashMap<>();
    }

    /** Returns the next task to be scheduled according to the execution graph set in place. */
    public Long nextTask() {
        if (taskSchedule.isEmpty()) {
            return null;
        }
        return taskSchedule.remove(0);
    }

    /**
     * Handles the visit with this event. Equivalent of running a single loop iteration of the Visit
     * method of the algorithm.
     *
     * @param event A {@link Event} that is used to update the execution graph.
     */
    @SuppressWarnings({"checkstyle:MissingSwitchDefault", "checkstyle:LeftCurly"})
    public void updateEvent(Event event) throws HaltTaskException, HaltExecutionException {
        if (!theExecutionGraph.isConsistent()) {
            event = Event.end();
        }

        if (event.getType() != Event.Type.INIT) {
            theExecutionGraph.addEvent(event);
            return;
        }

        switch (event.getType()) {
            case READ -> {
                Event maxWrite = theExecutionGraph.getMaxWriteForLocation(event.getLocation());
                theExecutionGraph.setReadsFrom(event, maxWrite);
                // TODO: update task schedule based on this.
            }
            case WRITE -> {
                backtrackSet.put(event, event);
            }
            case END -> backtrack();
        }
    }

    private void backtrack() {
        while (!theExecutionGraph.isEmpty()) {
            Event event = theExecutionGraph.top();

            if (event.getType() == Event.Type.READ) {
                Event corrWrite = theExecutionGraph.getReadsFrom(event);
                if (theExecutionGraph.existsWritesToLocationBefore(
                        event.getLocation(), corrWrite)) {
                    theExecutionGraph.setReadsFrom(
                            event,
                            theExecutionGraph.getMaxWriteForLocationBefore(
                                    event.getLocation(), corrWrite));
                    break;
                }
            } else if (event.getType() == Event.Type.WRITE) {
                Event backTrackEvent = backtrackSet.get(event);
                if (theExecutionGraph.existsReadsToLocationBefore(
                        event.getLocation(), backTrackEvent)) {
                    Event maxRead =
                            theExecutionGraph.getMaxReadForLocationBefore(
                                    event.getLocation(), backTrackEvent);
                    if (theExecutionGraph.isInCPrefix(maxRead, backTrackEvent)) {
                        backtrackSet.put(event, maxRead);
                        Set<Event> toDelete =
                                theExecutionGraph.eventsAfter(
                                        maxRead, (e) -> !theExecutionGraph.isInCPrefix(e, event));
                        // TODO: continue from here
                    }
                }
            }
        }
    }

    private boolean shouldRevisit(Event read, Event e) {}
}
