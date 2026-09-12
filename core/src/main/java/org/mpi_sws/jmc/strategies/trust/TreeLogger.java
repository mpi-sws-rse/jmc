package org.mpi_sws.jmc.strategies.trust;

import java.util.HashMap;
import java.util.Map;

/**
 * An optional diagnostic that renders the shape of the Trust exploration tree.
 *
 * <p>When enabled on {@link Algo}, this logger records, for each explored graph, the child graphs it
 * branches into — marking each child {@code F} (forward revisit) or {@code B} (backward revisit) —
 * together with running tallies of inconsistent graphs, blocked graphs, and leaf sizes. {@link
 * TrustStrategy#teardown} writes the assembled report to {@code trust-tree-logger.txt}. It has no
 * effect on the exploration itself.
 */
public class TreeLogger {

    /** The main tree rendering being built. */
    private final StringBuilder logger = new StringBuilder();

    /** The id of the graph currently being expanded. */
    private long graphId = 1L;

    /** Monotonic counter assigning ids to newly discovered child graphs. */
    private long graphCounter = 1L;

    /** Maps a pending exploration item to the graph id reserved for it. */
    private final Map<ExplorationStack.Item, Long> nextGraphIds = new HashMap<>();

    /** Running count of inconsistent graphs encountered. */
    private long numOfInconsistentGraphs = 0L;

    /** Running count of blocked (e.g. deadlocked) graphs encountered. */
    private long numOfBlockedGraphs = 0L;

    /** The list of inconsistent graph ids, as text. */
    private final StringBuilder inConsistentGraphLogger = new StringBuilder();

    /** The list of blocked graph ids, as text. */
    private final StringBuilder blockedGraphLogger = new StringBuilder();

    /** The list of leaf graph ids with their sizes, as text. */
    private final StringBuilder LeafSizeLogger = new StringBuilder();

    /** Whether the current graph produced any children (so it is not a leaf). */
    private boolean isBranching = false;

    /**
     * Begins a new branching line for the current graph, recording its size.
     *
     * @param sizeOfGraph the size of the current graph.
     */
    public void appendNewBranchs(int sizeOfGraph) {
        isBranching = true;
        logger.append(graphId).append("(").append(sizeOfGraph).append(")").append(" -> ");
    }

    /**
     * Records a (non-last) child graph for the given exploration item.
     *
     * @param item the pending exploration item.
     */
    public void appendNewChild(ExplorationStack.Item item) {
        graphCounter++;
        nextGraphIds.put(item, graphCounter);
        logger.append(graphCounter).append("(")
                .append(item.isBackwardRevisit() ? "B" : "F")
                .append("), ");
    }

    /**
     * Records the last child graph on the current branching line for the given item.
     *
     * @param item the pending exploration item.
     */
    public void appendLastChild(ExplorationStack.Item item) {
        graphCounter++;
        nextGraphIds.put(item, graphCounter);
        logger.append(graphCounter).append("(")
                .append(item.isBackwardRevisit() ? "B" : "F")
                .append(")").append(System.lineSeparator());
    }

    /** Records a "continue current graph" child (always a forward step). */
    public void appendContinueCurrent() {
        graphCounter++;
        logger.append(graphCounter).append("(F)").append(System.lineSeparator());
    }

    /** Appends a line separator to the rendering. */
    public void appendNextLine() {
        logger.append(System.lineSeparator());
    }

    /**
     * Advances the "current graph" to the one reserved for {@code nextItem}; if the previous graph
     * did not branch, it is recorded as a leaf of the given size.
     *
     * @param nextItem the item whose graph becomes current.
     * @param sizeOfGraph the size of the graph just finished.
     */
    public void updateLoggerGraphId(ExplorationStack.Item nextItem, int sizeOfGraph) {
        if (!isBranching) {
            addLeafSize(sizeOfGraph);
        }
        Long nextId = nextGraphIds.get(nextItem);
        nextGraphIds.remove(nextItem);
        if (nextId == null || nextId <= 0) {
            throw new IllegalStateException("Next graph ID not found for the given item.");
        }
        graphId = nextId;
        isBranching = false;
    }

    /**
     * Advances the "current graph" to the most recently created child; records a leaf if needed.
     *
     * @param sizeOfGraph the size of the graph just finished.
     */
    public void updateLoggerGraphIdWithLastGraph(int sizeOfGraph) {
        if (!isBranching) {
            addLeafSize(sizeOfGraph);
        }
        graphId = graphCounter;
        isBranching = false;
    }

    /**
     * Returns the assembled tree rendering.
     *
     * @return the main log buffer.
     */
    public StringBuilder getLogger() {
        return logger;
    }

    /**
     * Returns the id of the graph currently being expanded.
     *
     * @return the current graph id.
     */
    public long getGraphId() {
        return graphId;
    }

    /** Records the current graph as inconsistent. */
    public void addInconsistentGraph() {
        numOfInconsistentGraphs++;
        inConsistentGraphLogger.append(graphId).append(", ");
    }

    /** Records the current graph as blocked. */
    public void addBlockedGraph() {
        numOfBlockedGraphs++;
        blockedGraphLogger.append(graphId).append(", ");
    }

    /**
     * Records the current graph as a leaf of the given size.
     *
     * @param size the size of the leaf graph.
     */
    public void addLeafSize(int size) {
        LeafSizeLogger.append(graphId).append("(").append(size).append(")").append(", ");
    }

    /**
     * Returns the inconsistent-graph id list, or {@code null} if empty.
     *
     * @return the inconsistent-graph log, or {@code null}.
     */
    public StringBuilder getInConsistentGraphLogger() {
        if (inConsistentGraphLogger.length() == 0) {
            return null;
        }
        return inConsistentGraphLogger;
    }

    /**
     * Returns the blocked-graph id list, or {@code null} if empty.
     *
     * @return the blocked-graph log, or {@code null}.
     */
    public StringBuilder getBlockedGraphLogger() {
        if (blockedGraphLogger.length() == 0) {
            return null;
        }
        return blockedGraphLogger;
    }

    /**
     * Returns the leaf-size id list, or {@code null} if empty.
     *
     * @return the leaf-size log, or {@code null}.
     */
    public StringBuilder getLeafSizeLogger() {
        if (LeafSizeLogger.length() == 0) {
            return null;
        }
        return LeafSizeLogger;
    }

    /**
     * Returns the number of inconsistent graphs recorded.
     *
     * @return the inconsistent-graph count.
     */
    public long getNumOfInconsistentGraphs() {
        return numOfInconsistentGraphs;
    }

    /**
     * Returns the number of blocked graphs recorded.
     *
     * @return the blocked-graph count.
     */
    public long getNumOfBlockedGraphs() {
        return numOfBlockedGraphs;
    }
}
