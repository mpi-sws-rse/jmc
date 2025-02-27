package org.mpisws.jmc.strategies.trust;

import org.mpisws.jmc.runtime.HaltCheckerException;

import java.util.Set;

/**
 * Represents the revisit view of a lock read event. The two reads that are conflicting are
 * necessarily lock acquire exclusive reads.
 */
public class LockBackwardRevisitView {
    private final ExecutionGraphNode event;
    private final ExecutionGraphNode revisitRead;
    private final ExecutionGraph graph;

    /**
     * Creates a new backward revisit view for a lock read event.
     *
     * @param event The event that is revisited.
     * @param revisit The revisit read event.
     * @param graph The execution graph.
     */
    public LockBackwardRevisitView(
            ExecutionGraphNode event, ExecutionGraphNode revisit, ExecutionGraph graph) {
        this.graph = graph.clone();
        try {
            this.event = this.graph.getEventNode(event.key());
            this.revisitRead = this.graph.getEventNode(revisit.key());
        } catch (NoSuchEventException ignored) {
            throw new HaltCheckerException("The event or revisit read is not found.");
        }
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

    /**
     * Checks if the revisit read is revisit-able.
     *
     * @return True if the revisit read is revisit-able, false otherwise
     */
    public boolean isRevisitAble() {
        // Simple check if the revisit read is still porf before the event after updating the
        // reads-from, then this is not revisitable.
        // Note: not sure if this is sufficient, need to verify
        Set<Event.Key> writes = this.revisitRead.getPredecessors(Relation.ReadsFrom);
        if (writes.size() != 1) {
            throw new HaltCheckerException("The read event does not have a valid rf event.");
        }
        try {
            ExecutionGraphNode write = this.graph.getEventNode(writes.iterator().next());
            this.graph.changeReadsFrom(this.event, write);
            this.graph.recomputeVectorClocks();
        } catch (NoSuchEventException e) {
            throw new HaltCheckerException("The write of the revisit read is not found.");
        }
        return !this.revisitRead.happensBefore(this.event);
    }

    /**
     * Gets the restricted graph.
     *
     * @return The restricted graph
     */
    public ExecutionGraph getRestrictedGraph() {
        // Remove all events before the read and then add the revisit read, blocking the task
        this.graph.restrictTo(this.event);
        this.graph.addEvent(this.revisitRead.getEvent());
        return this.graph;
    }
}
