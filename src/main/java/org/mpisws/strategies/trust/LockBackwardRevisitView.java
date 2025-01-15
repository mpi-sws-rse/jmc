package org.mpisws.strategies.trust;

public class LockBackwardRevisitView {
    private final Event event;
    private final Event revisitRead;
    private final ExecutionGraph graph;

    public LockBackwardRevisitView(Event event, Event revisit, ExecutionGraph graph) {
        this.event = event;
        this.revisitRead = revisit;
        this.graph = graph;
    }

    public Event getEvent() {
        return event;
    }

    public Event getRevisitRead() {
        return revisitRead;
    }

    public ExecutionGraph getGraph() {
        return graph.clone();
    }

    public boolean isRevisitable() {
        // TODO: complete this method
        return false;
    }

    public ExecutionGraph getRestrictedGraph() {
        return this.graph;
    }
}
