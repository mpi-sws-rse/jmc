package org.mpi_sws.jmc.strategies.trust;

/**
 * The kinds of edge that connect nodes in an {@link ExecutionGraph}.
 *
 * <p>Each constant labels a directed edge between two {@link ExecutionGraphNode}s. Program order,
 * reads-from, and the thread relations are causal and propagate the Lamport vector clock; coherency
 * and {@link #FR} are consistency constraints only and are deliberately excluded from clock
 * propagation (see {@code ExecutionGraphNode.addBackEdge}).
 */
public enum Relation {
    /** From a write to a read that observes it (rf). */
    ReadsFrom("readsFrom"),
    /** Between successive writes to the same location, giving the modification order (co). */
    Coherency("coherency"),
    /** From an event to the next event of the same task (po). */
    ProgramOrder("programOrder"),
    /** Total order between thread-start events (maintained on {@link LocationStore#ThreadLocation}). */
    ThreadCreation("threadCreation"),
    /** From a spawning task's event to the spawned task's start event. */
    ThreadStart("threadStart"),
    /** From the last event of a joined task to the join event. */
    ThreadJoin("threadJoin"),
    /** From a join request to its completion. */
    ThreadJoinCompletion("threadJoinCompletion"),
    /** From-read: {@code FR = rf^-1 ; co}, from a read to the co-successor of its source write. */
    FR("fr"),
    ;

    /** The stable string key used when serializing/printing this relation. */
    private final String key;

    /**
     * Returns the string key of this relation.
     *
     * @return the relation key.
     */
    public String key() {
        return key;
    }

    /**
     * Creates a relation constant with the given string key.
     *
     * @param key the string key.
     */
    private Relation(String key) {
        this.key = key;
    }

    /**
     * Returns the relation's string key.
     *
     * @return the relation key.
     */
    @Override
    public String toString() {
        return key;
    }
}
