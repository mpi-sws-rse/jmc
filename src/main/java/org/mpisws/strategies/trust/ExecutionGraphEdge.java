package org.mpisws.strategies.trust;

public class ExecutionGraphEdge {
    private final ExecutionGraphNode node1;
    private final ExecutionGraphNode node2;
    private final ExecutionGraphAdjacency adjacency;

    public ExecutionGraphEdge(ExecutionGraphNode node1, ExecutionGraphNode node2, ExecutionGraphAdjacency adjacency) {
        this.node1 = node1;
        this.node2 = node2;
        this.adjacency = adjacency;
    }

    public ExecutionGraphNode getNode1() {
        return node1;
    }

    public ExecutionGraphNode getNode2() {
        return node2;
    }

    public ExecutionGraphAdjacency getAdjacency() {
        return adjacency;
    }
}
