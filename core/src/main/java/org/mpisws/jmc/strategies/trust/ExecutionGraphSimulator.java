package org.mpisws.jmc.strategies.trust;

import org.mpisws.jmc.runtime.RuntimeEvent;

import java.util.List;

public class ExecutionGraphSimulator {

    private ExecutionGraph executionGraph;

    public ExecutionGraphSimulator() {
        this.executionGraph = new ExecutionGraph();
        this.executionGraph.addEvent(Event.init());
    }

    public ExecutionGraph getExecutionGraph() {
        return executionGraph;
    }

    public void updateEvent(RuntimeEvent event) {
        // Update the execution graph based on the event
        List<Event> trustEvents = EventFactory.fromRuntimeEvent(event);
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
                    handleRead(trustEvent);
                    break;
                case WRITE_EX:
                    handleWrite(trustEvent);
                    break;
                case LOCK_AWAIT:
                    handleLockAwait(trustEvent);
                    break;
                case NOOP:
                    handleNoop(trustEvent);
                    break;
            }
        }
    }

    public void reset() {
        this.executionGraph = new ExecutionGraph();
        this.executionGraph.addEvent(Event.init());
    }

    public void handleBot(Event event) {
        executionGraph.addEvent(event);
    }

    public void handleRead(Event event) {
        ExecutionGraphNode read = executionGraph.addEvent(event);
        ExecutionGraphNode coMaxWrite = executionGraph.getCoMax(event.getLocation());
        executionGraph.setReadsFrom(read, coMaxWrite);
    }

    public void handleWrite(Event event) {
        ExecutionGraphNode write = executionGraph.addEvent(event);
        executionGraph.trackCoherency(write);
    }

    public void handleReadEx(Event event) {
        ExecutionGraphNode write = executionGraph.addEvent(event);
        executionGraph.trackCoherency(write);
    }

    public void handleWriteEx(Event event) {
        ExecutionGraphNode writeNode = executionGraph.addEvent(event);
        executionGraph.trackCoherency(writeNode);
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
