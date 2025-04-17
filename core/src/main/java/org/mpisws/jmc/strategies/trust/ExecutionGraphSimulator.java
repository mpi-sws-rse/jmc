package org.mpisws.jmc.strategies.trust;

import org.mpisws.jmc.runtime.RuntimeEvent;

import java.util.List;

public class ExecutionGraphSimulator {

    private ExecutionGraph executionGraph;

    public ExecutionGraphSimulator() {
        this.executionGraph = new ExecutionGraph();
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
                    handleReadEx(trustEvent);
                    break;
                case WRITE_EX:
                    handleWriteEx(trustEvent);
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
    }

    public void handleBot(Event event) {
    }

    public void handleRead(Event event) {
        executionGraph.addEvent(event);
    }

    public void handleWrite(Event event) {
        executionGraph.addEvent(event);
    }

    public void handleReadEx(Event event) {
        executionGraph.addEvent(event);
    }

    public void handleWriteEx(Event event) {
        executionGraph.addEvent(event);
    }

    public void handleLockAwait(Event event) {
        executionGraph.addEvent(event);
    }

    public void handleNoop(Event event) {
        executionGraph.addEvent(event);
    }

}
