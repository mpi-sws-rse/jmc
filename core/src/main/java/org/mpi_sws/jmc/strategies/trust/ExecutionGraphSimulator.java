package org.mpi_sws.jmc.strategies.trust;

import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Rebuilds a straight-line execution graph from the events of a single interleaving, for coverage
 * measurement.
 *
 * <p>Unlike {@link Algo}, this simulator does no exploration: it simply follows whatever schedule
 * the wrapped strategy produced, adding each event and making every read observe the current
 * coherence-maximal write. {@link MeasureGraphCoverageStrategy} feeds it the same events the real
 * strategy sees and then hashes the resulting {@link ExecutionGraph} / {@link CoverageGraph} to
 * count how many <em>distinct</em> execution graphs a run explored.
 */
public class ExecutionGraphSimulator {

    /** The graph being reconstructed for the current iteration. */
    private ExecutionGraph executionGraph;

    /** A parallel, lighter-weight record of the po/rf/co relations for coverage hashing. */
    private CoverageGraph coverageGraph;

    /** Creates a simulator with an empty graph seeded with the init event. */
    public ExecutionGraphSimulator() {
        this.executionGraph = new ExecutionGraph();
        this.coverageGraph = new CoverageGraph();
        this.executionGraph.addEvent(Event.init());
    }

    /**
     * Returns the reconstructed execution graph.
     *
     * @return the current execution graph.
     */
    public ExecutionGraph getExecutionGraph() {
        return executionGraph;
    }

    /**
     * Returns the parallel coverage graph.
     *
     * @return the current coverage graph.
     */
    public CoverageGraph getCoverageGraph() {
        return coverageGraph;
    }

    /**
     * Translates a runtime event and applies its trust events to the graph. Lock-acquire events are
     * ignored (locks are not modeled by the simulator).
     *
     * <p>Not for use outside {@link MeasureGraphCoverageStrategy}.
     *
     * @param event the runtime event.
     */
    public void updateEvent(JmcRuntimeEvent event) {
        List<Event> trustEvents = EventFactory.fromRuntimeEvent(event);
        // Update the execution graph based on the event
        if (event.getType() == JmcRuntimeEvent.Type.LOCK_ACQUIRE_EVENT) {
            return;
        }
        for (Event trustEvent : trustEvents) {
            updateEvent(trustEvent);
        }
    }

    /**
     * Applies a single trust event to the graph, dispatching on its type.
     *
     * @param event the trust event.
     */
    public void updateEvent(Event event) {
        // Add PO
        coverageGraph.addPo(event);
        switch (event.getType()) {
            case END:
                handleBot(event);
                break;
            case READ:
                handleRead(event);
                break;
            case WRITE:
                handleWrite(event);
                break;
            case READ_EX:
                handleReadEx(event);
                break;
            case WRITE_EX:
                handleWriteEx(event);
                break;
            case NOOP:
                handleNoop(event);
                break;
        }
    }

    /** Resets to a fresh graph seeded with the init event, for the next iteration. */
    public void reset() {
        this.executionGraph = new ExecutionGraph();
        this.coverageGraph = new CoverageGraph();
        this.executionGraph.addEvent(Event.init());
    }

    /**
     * Handles the end-of-execution event (no-op for the simulator).
     *
     * @param event the end event.
     */
    public void handleBot(Event event) {
        // SKIP
    }

    /**
     * Adds a read and makes it observe the current coherence-maximal write.
     *
     * @param event the read event.
     */
    public void handleRead(Event event) {
        ExecutionGraphNode read = executionGraph.addEvent(event);
        ExecutionGraphNode coMaxWrite = executionGraph.getCoMax(event.getLocation());
        executionGraph.setReadsFrom(read, coMaxWrite);
        // Track the rf
        coverageGraph.addRf(event);
    }

    /**
     * Adds a write and makes it coherence-maximal.
     *
     * @param event the write event.
     */
    public void handleWrite(Event event) {
        ExecutionGraphNode write = executionGraph.addEvent(event);
        executionGraph.trackCoherency(write);
        // Track the CO (MO)
        coverageGraph.addCo(event);
    }

    /**
     * Adds the read half of a read-modify-write, observing the coherence-maximal write.
     *
     * @param event the exclusive read event.
     */
    public void handleReadEx(Event event) {
        ExecutionGraphNode write = executionGraph.addEvent(event);
        ExecutionGraphNode coMaxRead = executionGraph.getCoMax(event.getLocation());
        executionGraph.setReadsFrom(write, coMaxRead);
        coverageGraph.addRf(event);
    }

    /**
     * Adds the write half of a read-modify-write and makes it coherence-maximal.
     *
     * @param event the exclusive write event.
     */
    public void handleWriteEx(Event event) {
        ExecutionGraphNode writeNode = executionGraph.addEvent(event);
        executionGraph.trackCoherency(writeNode);
        // Track the CO (MO)
        coverageGraph.addCo(event);
    }

    /**
     * Handles a lock-await event (no-op for the simulator).
     *
     * @param event the event.
     */
    public void handleLockAwait(Event event) {
        // SKIP
    }

    /**
     * Adds a no-op event and records thread-creation / start / join relations when applicable.
     *
     * @param event the no-op event.
     */
    public void handleNoop(Event event) {
        ExecutionGraphNode eventNode = executionGraph.addEvent(event);
        if (EventUtils.isThreadStart(event)) {
            // Track thread creation coherency
            executionGraph.trackThreadCreates(eventNode);
            if (event.getTaskId() != 0) { // Skip the main thread
                // Track thread start dependencies
                executionGraph.trackThreadStarts(eventNode);
            }
        } else if (EventUtils.isThreadJoin(event)) {
            executionGraph.trackThreadJoins(eventNode);
        }
    }

    /**
     * Returns the program-order-maximal event of every task.
     *
     * @return the last event of each task.
     */
    public List<Event> getAllPoMaxEvents() {
        List<ExecutionGraphNode> poMaxEvents = executionGraph.getAllPoMaxNode();
        List<Event> events = new ArrayList<>();
        for (ExecutionGraphNode node : poMaxEvents) {
            events.add(node.getEvent());
        }
        return events;
    }

    /**
     * Returns the program-order-maximal event of every task, excluding no-ops that are not thread
     * finishes.
     *
     * @return the filtered po-maximal events.
     */
    public List<Event> getAllNonNoopPoMaxEvents() {
        List<ExecutionGraphNode> poMaxEvents = executionGraph.getAllPoMaxNode();
        List<Event> events = new ArrayList<>();
        for (ExecutionGraphNode node : poMaxEvents) {
            if (!EventUtils.isNoop(node.getEvent()) || EventUtils.isThreadFinish(node.getEvent())) {
                events.add(node.getEvent());
            }
        }
        return events;
    }

    /**
     * Delegates to {@link ExecutionGraph#isStartMaxWithStarter(Event)}.
     *
     * @param event a thread-start event.
     * @return whether the starter's po-max event is still the cause of this start.
     */
    public boolean isStartMaxWithStarter(Event event) {
        return executionGraph.isStartMaxWithStarter(event);
    }

    /**
     * Delegates to {@link ExecutionGraph#isCoMax(Event)}.
     *
     * @param event a write event.
     * @return whether the write is coherence-maximal.
     */
    public boolean isCoMax(Event event) {
        return executionGraph.isCoMax(event);
    }

    /**
     * Delegates to {@link ExecutionGraph#isRfMax(Event)}.
     *
     * @param event a write event.
     * @return whether the write has no reader (reads-from-maximal).
     */
    public boolean isRfMax(Event event) {
        return executionGraph.isRfMax(event);
    }

    /**
     * Delegates to {@link ExecutionGraph#isFrMax(Event)}.
     *
     * @param event a read event.
     * @return whether the read's source write is coherence-maximal.
     */
    public boolean isFrMax(Event event) {
        return executionGraph.isFrMax(event);
    }

    /**
     * Delegates to {@link ExecutionGraph#isTcMax(Event)}.
     *
     * @param event a thread-start event.
     * @return whether it is thread-creation-maximal.
     */
    public boolean isTcMax(Event event) {
        return executionGraph.isTcMax(event);
    }

    /**
     * Delegates to {@link ExecutionGraph#isStMax(Event)}.
     *
     * @param event an event.
     * @return whether it is thread-start-maximal.
     */
    public boolean isStMax(Event event) {
        return executionGraph.isStMax(event);
    }

    /**
     * Delegates to {@link ExecutionGraph#isJtMax(Event)}.
     *
     * @param event a thread-finish event.
     * @return whether it is thread-join-maximal.
     */
    public boolean isJtMax(Event event) {
        return executionGraph.isJtMax(event);
    }

    /**
     * Returns the task that started the given task (from that task's first, thread-start, event).
     *
     * @param tid the started task id.
     * @return the id of the starting task.
     */
    public long getStarterTid(long tid) {
        ExecutionGraphNode firstNode = executionGraph.getFirstEventOfTask(tid);
        if (!EventUtils.isThreadStart(firstNode.getEvent())) {
            throw new IllegalArgumentException("The first event of the task is not a START event");
        }
        return EventUtils.getStartedBy(firstNode.getEvent());
    }

    /**
     * Returns the last (program-order-maximal) event of the given task.
     *
     * @param tid the task id.
     * @return the task's last event.
     */
    public Event getLastEventOfTask(long tid) {
        ExecutionGraphNode lastNode = executionGraph.getLastNodeOfTask(tid);
        if (lastNode == null) {
            throw new IllegalArgumentException("No event found for task: " + tid);
        }
        return lastNode.getEvent();
    }
}
