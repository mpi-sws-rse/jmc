package org.mpisws.strategies.trust;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Represents an execution graph.
 *
 * <p>Contains the exploration and all the relations defined according to the Trust algorithm
 *
 * <p>Some terminology to understand the code
 *
 * <ul>
 *   <li>TO: Total order of events observed in this execution graph, in the order they were added
 *   <li>PO: Program order. A union of reads from partial order and the total order of events per
 *       task
 *   <li>RF: Reads from relation between reads and writes
 *   <li>CO: A coherency order between writes
 * </ul>
 */
public class ExecutionGraph {

    private static final Logger LOGGER = LogManager.getLogger(ExecutionGraph.class);
    // Total order of events observed in this execution graph
    private List<Event> allEvents;

    /** Initializes a new execution graph. */
    public ExecutionGraph() {
        this.allEvents = new ArrayList<>();
    }

    /**
     * Adds an event to the execution graph.
     *
     * @param event The event to add.
     */
    public void addEvent(Event event) {
        allEvents.add(event);
    }

    public Event getMaxWriteForLocation(Location location) {
        // TODO: Implement this method
        return null;
    }

    /**
     * Returns the maximum write event for the given location that is prior (TO) to the given event.
     *
     * @param location The location to check.
     * @param priorToWrite The event that the write should be prior to.
     * @return The maximum write event for the given location that is prior to the given event.
     */
    public Event getMaxWriteForLocationBefore(Location location, Event priorToWrite) {
        // TODO: Implement this method
        return null;
    }

    /**
     * Returns true if there exists a write to the given location before (TO) the given event.
     *
     * @param location The location to check.
     * @param event The event to check.
     * @return True if there exists a write to the given location before the given event.
     */
    public boolean existsWritesToLocationBefore(Location location, Event event) {
        // TODO: Implement this method
        return false;
    }

    /**
     * Returns true if there exists a read to the given location before (TO) the given event.
     *
     * @param location The location to check.
     * @param event The event to check.
     * @return True if there exists a read to the given location before the given event.
     */
    public boolean existsReadsToLocationBefore(Location location, Event event) {
        // TODO: Implement this method
        return false;
    }

    /**
     * Returns the maximum read event for the given location that is prior (TO) to the given event.
     *
     * @param location The location to check.
     * @param priorToRead The event that the read should be prior to.
     * @return The maximum read event for the given location that is prior to the given event.
     */
    public Event getMaxReadForLocationBefore(Location location, Event priorToRead) {
        // TODO: Implement this method
        return null;
    }

    /**
     * Returns true if event1 is in the C-prefix of event2.
     *
     * @param event1 .
     * @param event2 .
     * @return True if event1 is in the C-prefix of event2.
     */
    public boolean isInCPrefix(Event event1, Event event2) {
        // TODO: Implement this method
        return false;
    }

    /**
     * Returns the set of events that are after (TO) the given event and satisfy the given
     * predicate.
     *
     * @param event The event to check.
     * @param predicate The predicate to satisfy.
     * @return The set of events that are after the given event and satisfy the given predicate.
     */
    public Set<Event> eventsAfter(Event event, Event.EventPredicate predicate) {
        return null;
    }

    /**
     * Returns the event that the given event reads from.
     *
     * @param event The event to check.
     * @return The event that the given event reads from.
     */
    public Event getReadsFrom(Event event) {
        // TODO: Implement this method
        return null;
    }

    public void setReadsFrom(Event event, Event maxWrite) {
        // TODO: Implement this method
    }

    public boolean isConsistent() {
        // TODO: Implement this method
        return true;
    }

    /** Returns true if the graph contains only the initial event. */
    public boolean isEmpty() {
        return allEvents.size() == 1 && allEvents.get(0).isInit();
    }

    /**
     * Returns the last event in the execution graph.
     *
     * @return The last event in the execution graph.
     */
    public Event top() {
        return allEvents.get(allEvents.size() - 1);
    }
}
