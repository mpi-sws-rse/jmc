package org.mpi_sws.jmc.strategies.trust;

import java.util.HashMap;
import java.util.Map;

public class TreeLogger {

    private final StringBuilder logger = new StringBuilder();

    private long graphId = 1L;

    private long graphCounter = 1L;

    private final Map<ExplorationStack.Item, Long> nextGraphIds = new HashMap<>();

    private long numOfInconsistentGraphs = 0L;

    private long numOfBlockedGraphs = 0L;

    private final StringBuilder inConsistentGraphLogger = new StringBuilder();

    private final StringBuilder blockedGraphLogger = new StringBuilder();

    private final StringBuilder LeafSizeLogger = new StringBuilder();

    private boolean isBranching = false;

    public void appendNewBranchs(int sizeOfGraph) {
        isBranching = true;
        logger.append(graphId).append("(").append(sizeOfGraph).append(")").append(" -> ");
    }

    public void appendNewChild(ExplorationStack.Item item) {
        graphCounter++;
        nextGraphIds.put(item, graphCounter);
        logger.append(graphCounter).append("(")
                .append(item.isBackwardRevisit() ? "B" : "F")
                .append("), ");
    }

    public void appendLastChild(ExplorationStack.Item item) {
        graphCounter++;
        nextGraphIds.put(item, graphCounter);
        logger.append(graphCounter).append("(")
                .append(item.isBackwardRevisit() ? "B" : "F")
                .append(")").append(System.lineSeparator());
    }

    public void appendContinueCurrent() {
        graphCounter++;
        logger.append(graphCounter).append("(F)").append(System.lineSeparator());
    }

    public void appendNextLine() {
        logger.append(System.lineSeparator());
    }

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

    public void updateLoggerGraphIdWithLastGraph(int sizeOfGraph) {
        if (!isBranching) {
            addLeafSize(sizeOfGraph);
        }
        graphId = graphCounter;
        isBranching = false;
    }

    public StringBuilder getLogger() {
        return logger;
    }

    public long getGraphId() {
        return graphId;
    }

    public void addInconsistentGraph() {
        numOfInconsistentGraphs++;
        inConsistentGraphLogger.append(graphId).append(", ");
    }

    public void addBlockedGraph() {
        numOfBlockedGraphs++;
        blockedGraphLogger.append(graphId).append(", ");
    }

    public void addLeafSize(int size) {
        LeafSizeLogger.append(graphId).append("(").append(size).append(")").append(", ");
    }

    public StringBuilder getInConsistentGraphLogger() {
        if (inConsistentGraphLogger.length() == 0) {
            return null;
        }
        return inConsistentGraphLogger;
    }

    public StringBuilder getBlockedGraphLogger() {
        if (blockedGraphLogger.length() == 0) {
            return null;
        }
        return blockedGraphLogger;
    }

    public StringBuilder getLeafSizeLogger() {
        if (LeafSizeLogger.length() == 0) {
            return null;
        }
        return LeafSizeLogger;
    }

    public long getNumOfInconsistentGraphs() {
        return numOfInconsistentGraphs;
    }

    public long getNumOfBlockedGraphs() {
        return numOfBlockedGraphs;
    }
}
