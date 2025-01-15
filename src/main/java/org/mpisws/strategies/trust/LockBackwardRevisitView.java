package org.mpisws.strategies.trust;

public class LockBackwardRevisitView {
    private final ExecutionGraphNode event;
    private final ExecutionGraphNode revisitRead;
    private final ExecutionGraph graph;

    public LockBackwardRevisitView(
            ExecutionGraphNode event, ExecutionGraphNode revisit, ExecutionGraph graph) {
        this.event = event;
        this.revisitRead = revisit;
        this.graph = graph;
    }

    public ExecutionGraphNode getEvent() {
        return event;
    }

    public ExecutionGraphNode getRevisitRead() {
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
