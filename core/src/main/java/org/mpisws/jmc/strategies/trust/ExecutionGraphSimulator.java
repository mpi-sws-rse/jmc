package org.mpisws.jmc.strategies.trust;

import org.mpisws.jmc.runtime.JmcRuntimeEvent;

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

    public void updateEvent(JmcRuntimeEvent event) {
        List<Event> trustEvents = EventFactory.fromRuntimeEvent(event);
        // Update the execution graph based on the event
        if (event.getType() == JmcRuntimeEvent.Type.LOCK_ACQUIRE_EVENT) {
            return;
        } else if (event.getType() == JmcRuntimeEvent.Type.LOCK_ACQUIRED_EVENT) {
            Event event1 =
                    new Event(
                            event.getTaskId() - 1,
                            Location.fromRuntimeEvent(event).hashCode(),
                            Event.Type.READ_EX);
            event1.setAttribute("lock_acquire", true);
            Event event2 =
                    new Event(
                            event.getTaskId() - 1,
                            Location.fromRuntimeEvent(event).hashCode(),
                            Event.Type.WRITE_EX);
            event2.setAttribute("lock_acquire", true);
            trustEvents = List.of(event1, event2);
        }
        for (Event trustEvent : trustEvents) {
            switch (trustEvent.getType()) {
                case END:
                    handleBot(trustEvent);
                    break;
                case READ:
                    handleRead(trustEvent);
                    break;
                case WRITE:
                    handleWrite(trustEvent);
                    break;
                case READ_EX:
                    handleReadEx(trustEvent);
                    break;
                case WRITE_EX:
                    handleWriteEx(trustEvent);
                    break;
                case NOOP:
                    handleNoop(trustEvent);
                    break;
            }
            // Add PO
            coverageGraph.addPo(trustEvent);
        }
    }

    public void reset() {
        this.executionGraph = new ExecutionGraph();
        this.coverageGraph = new CoverageGraph();
        this.executionGraph.addEvent(Event.init());
    }

    public void handleBot(Event event) {
        //        executionGraph.addEvent(event);
    }

    public void handleRead(Event event) {
        ExecutionGraphNode read = executionGraph.addEvent(event);
        ExecutionGraphNode coMaxWrite = executionGraph.getCoMax(event.getLocation());
        executionGraph.setReadsFrom(read, coMaxWrite);
        coverageGraph.addRf(event);
    }

    public void handleWrite(Event event) {
        ExecutionGraphNode write = executionGraph.addEvent(event);
        executionGraph.trackCoherency(write);
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
        coverageGraph.addCo(event);
    }

    public void handleLockAwait(Event event) {}

    public void handleNoop(Event event) {
        ExecutionGraphNode eventNode = executionGraph.addEvent(event);
        if (EventUtils.isThreadStart(event)) {
            executionGraph.trackThreadCreates(eventNode);
            if (event.getTaskId() != 0) { // Skip the main thread
                executionGraph.trackThreadStarts(eventNode);
            }
        } else if (EventUtils.isThreadJoin(event)) {
            executionGraph.trackThreadJoins(eventNode);
        }
    }
}
