package org.mpisws.jmc.strategies.trust;

import org.mpisws.jmc.runtime.JmcRuntimeEvent;

import java.util.ArrayList;
import java.util.List;

public class ExecutionGraphSimulator {

    private ExecutionGraph executionGraph;

    private CoverageGraph coverageGraph;

    public ExecutionGraphSimulator() {
        this.executionGraph = new ExecutionGraph();
        this.coverageGraph = new CoverageGraph();
        this.executionGraph.addEvent(Event.init());
    }

    public ExecutionGraph getExecutionGraph() {
        return executionGraph;
    }

    public CoverageGraph getCoverageGraph() {
        return coverageGraph;
    }

    // Do not use this method outside the scop of `MeasureGraphCoverageStrategy` class
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

    // Using this method to update the graph with trust event
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

    public void reset() {
        this.executionGraph = new ExecutionGraph();
        this.coverageGraph = new CoverageGraph();
        this.executionGraph.addEvent(Event.init());
    }

    public void handleBot(Event event) {
        // SKIP
    }

    public void handleRead(Event event) {
        ExecutionGraphNode read = executionGraph.addEvent(event);
        ExecutionGraphNode coMaxWrite = executionGraph.getCoMax(event.getLocation());
        executionGraph.setReadsFrom(read, coMaxWrite);
        // Track the rf
        coverageGraph.addRf(event);
    }

    public void handleWrite(Event event) {
        ExecutionGraphNode write = executionGraph.addEvent(event);
        executionGraph.trackCoherency(write);
        // Track the CO (MO)
        coverageGraph.addCo(event);
    }

    public void handleReadEx(Event event) {
        ExecutionGraphNode write = executionGraph.addEvent(event);
        ExecutionGraphNode coMaxRead = executionGraph.getCoMax(event.getLocation());
        executionGraph.setReadsFrom(write, coMaxRead);
        coverageGraph.addRf(event);
    }

    public void handleWriteEx(Event event) {
        ExecutionGraphNode writeNode = executionGraph.addEvent(event);
        executionGraph.trackCoherency(writeNode);
        // Track the CO (MO)
        coverageGraph.addCo(event);
    }

    public void handleLockAwait(Event event) {
        // SKIP
    }

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

    public List<Event> getAllPoMaxEvents() {
        List<ExecutionGraphNode> poMaxEvents = executionGraph.getAllPoMaxNode();
        List<Event> events = new ArrayList<>();
        for (ExecutionGraphNode node : poMaxEvents) {
            events.add(node.getEvent());
        }
        return events;
    }

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

    public boolean isStartMaxWithStarter(Event event) {
        return executionGraph.isStartMaxWithStarter(event);
    }

    public boolean isCoMax(Event event) {
        return executionGraph.isCoMax(event);
    }

    public boolean isRfMax(Event event) {
        return executionGraph.isRfMax(event);
    }

    public boolean isFrMax(Event event) {
        return executionGraph.isFrMax(event);
    }

    public boolean isTcMax(Event event) {
        return executionGraph.isTcMax(event);
    }

    public boolean isStMax(Event event) {
        return executionGraph.isStMax(event);
    }

    public boolean isJtMax(Event event) {
        return executionGraph.isJtMax(event);
    }

    public long getStarterTid(long tid) {
        ExecutionGraphNode firstNode = executionGraph.getFirstEventOfTask(tid);
        if (!EventUtils.isThreadStart(firstNode.getEvent())) {
            throw new IllegalArgumentException("The first event of the task is not a START event");
        }
        return EventUtils.getStartedBy(firstNode.getEvent());
    }

    public Event getLastEventOfTask(long tid) {
        ExecutionGraphNode lastNode = executionGraph.getLastNodeOfTask(tid);
        if (lastNode == null) {
            throw new IllegalArgumentException("No event found for task: " + tid);
        }
        return lastNode.getEvent();
    }
}
