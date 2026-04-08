package org.mpi_sws.jmc.strategies.estimation.trust.testor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.runtime.HaltCheckerException;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.strategies.estimation.MetaTreeEstimator;
import org.mpi_sws.jmc.strategies.trust.*;

import java.util.*;

public class Testor implements MetaTreeEstimator {

    private static final Logger LOGGER = LogManager.getLogger(Testor.class);
    private final int budget;
    protected boolean reExecutionNeeded = false;
    protected float expectedValue = 0.0f;
    protected float prod = 1.0f;
    private final List<ExplorationStack.Item> current = new ArrayList<>();
    private final Map<ExplorationStack.Item, Boolean> currentLeaves = new HashMap<>();
    private final List<ExplorationStack.Item> next = new ArrayList<>();
    private ExplorationStack.Item currentItem;

    public Testor(int budget) {
        this.budget = budget;
        ExplorationStack.Item dummy = ExplorationStack.Item.continueCurrent();
        currentLeaves.put(dummy, true);
        currentItem = dummy;
    }

    public Testor() {
        this(2);
    }

    /**
     * @param alg
     * @throws HaltTaskException
     * @throws HaltExecutionException
     */
    @Override
    public void updateTree(Algo alg) throws HaltTaskException, HaltExecutionException {
        // If we are guiding, we should not update the tree or the frontier.
        if (alg.areWeGuiding()) {
            return;
        }

        // Fetch the reachable nodes from the current node in the current frontier
        List<ExplorationStack.Item> items = retrieveItems(alg);
        if (items.isEmpty()) {
            return;
        }

        // Update the leaves map for the current frontier.
        updateLeaves();

        // Add a node representing the sc-max child which will be not enumerated by the algorithm but
        // will be used for estimation.
        appendCurrentItem(items, alg);

        // Accumulate the reachable nodes into the next frontier
        updateNext(items);
        items = null; // Help GC

        updateStack(alg);
    }

    private void updateTreeBW(Algo alg) throws HaltTaskException, HaltExecutionException {
        List<ExplorationStack.Item> items = retrieveItems(alg);
        if (items.isEmpty() || items.size() < 1) {
            throw HaltExecutionException.error("The number of items in the stack is less than 2");
        }

        // Update the leaves map for the current frontier.
        updateLeaves();

        // Add a node representing the FLW which we handled internally in order to fix the co-edge.
        appendCurrentItem(items, alg);

        // Accumulate the reachable nodes into the next frontier
        updateNext(items);
        items = null; // Help GC

        updateStack(alg);
    }

    private void updateLeaves() {
        if (currentItem != null && currentLeaves.containsKey(currentItem)) {
            currentLeaves.put(currentItem, false);
        }
    }

    private List<ExplorationStack.Item> retrieveItems(Algo alg) {
        ExplorationStack stack = alg.getExplorationStack();
        List<ExplorationStack.Item> items = new ArrayList<>();
        while (!stack.isEmpty()) {
            items.add(stack.pop());
        }
        return items;
    }

    private boolean appendCurrentItem(List<ExplorationStack.Item> items, Algo alg) {
        if (items.isEmpty() || alg == null || alg.getExecutionGraph() == null) {
            return false;
        }
        ExplorationStack.Item currItem = ExplorationStack.Item.continueCurrent(alg.getExecutionGraph());
        items.add(currItem);
        return true;
    }

    private boolean updateNext(List<ExplorationStack.Item> items) {
        if (items.isEmpty() || next == null) {
            return false;
        }
        next.addAll(items);
        items.clear();
        items = null; // Help GC
        return true;
    }

    private boolean updateFrontier() {
        if (next.isEmpty() || !current.isEmpty()) {
            return false;
        }

        List<ExplorationStack.Item> candidate = randomSelection(next);
        next.clear();

        List<ExplorationStack.Item> cloned = makeClone(candidate);
        candidate.clear();
        candidate = null; // Help GC

        current.addAll(cloned);
        currentLeaves.clear();
        for (ExplorationStack.Item item : cloned) {
            currentLeaves.put(item, true);
        }
        cloned.clear();
        cloned = null; // Help GC
        return true;
    }

    private void updateProd() {
        float d = (float) next.size() / (float) countFrontier();
        prod = prod * d;
    }

    private void updateEstimation() {
        expectedValue = expectedValue + (prod * ((float) countLeaves() / (float) countFrontier()));
    }

    private int countFrontier() {
        return currentLeaves.size();
    }

    private int countLeaves() {
        int count = 0;
        if (currentLeaves.isEmpty()) {
            return count;
        }

        /*for (Boolean isLeaf : currentLeaves.values()) {
            if (isLeaf) {
                count++;
            }
        }*/
        for (Map.Entry<ExplorationStack.Item, Boolean> entry : currentLeaves.entrySet()) {
            if (entry.getValue() &&
                    entry.getKey().getGraph() != null &&
                    entry.getKey().getGraph().isConsistent()) {
                count++;
            }
        }
        return count;
    }

    private ExplorationStack.Item getNextFrontier() {
        if (current.isEmpty()) {
            return null;
        }
        return current.remove(0);
    }

    private boolean exploreNextFrontier(Algo alg) {
        ExplorationStack.Item nextItem = getNextFrontier();
        if (nextItem == null) {
            return false;
        }

        if (nextItem.isBackwardRevisit()) {
            return handleBWR(alg, nextItem);
        } else {
            return handleNonBWR(alg, nextItem);
        }
    }

    private boolean handleBWR(Algo alg, ExplorationStack.Item nextItem) {
        alg.processBWR(nextItem);
        if (alg.getExplorationStack().size() > 1) {
            fixCoEdge(alg);
            currentItem = nextItem;
            updateTreeBW(alg);
            return true;
        } else {
            ExplorationStack.Item top = alg.getExplorationStack().pop();
            currentLeaves.remove(nextItem);
            currentLeaves.put(top, true);
            return handleNonBWR(alg, top);
        }
    }

    private boolean handleNonBWR(Algo alg, ExplorationStack.Item nextItem) {
        ExplorationStack stack = alg.getExplorationStack();
        stack.push(nextItem);
        currentItem = nextItem;
        reExecutionNeeded = true;
        return true;
    }

    private void fixCoEdge(Algo alg) {
        ExplorationStack.Item top = alg.getExplorationStack().pop();
        if (!top.isLastWriteRevisit()) {
            throw HaltCheckerException.error("The top item in the stack is not a last write revisit.");
        }
        alg.setExecutionGraph(top.getGraph());
        alg.processFLW(top);

        top = null; // Help GC
    }

    // This method will pick at most `budget` number of items from the given list of items. If the number of items is less
    // than or equal to the budget, it will return all the items. Otherwise, it will randomly select budget number of
    // distinct items from the list and return them. (Fisher-Yates partial shuffle)
    private List<ExplorationStack.Item> randomSelection(List<ExplorationStack.Item> items) {
        if (items.size() <= budget) {
            return new ArrayList<>(items);
        }

        List<ExplorationStack.Item> copy = new ArrayList<>(items);
        items = null; // Help GC

        for (int i = 0; i < budget; i++) {
            int j = i + (int) (Math.random() * (copy.size() - i));
            // Swap elements at i and j
            Collections.swap(copy, i, j);
        }

        return copy.subList(0, budget);
    }

    private List<ExplorationStack.Item> makeClone(List<ExplorationStack.Item> items) {
        List<ExplorationStack.Item> clones = new ArrayList<>();
        for (ExplorationStack.Item item : items) {
            // If an item is not a BWR, we need to update the item's graph with a cloned graph
            if (!item.isBackwardRevisit()) {
                ExecutionGraph cln = item.getGraph().clone();
                ExecutionGraphNode e1 = null;
                if (item.getEvent1() != null) {
                    try {
                        e1 = cln.getEventNode(item.getEvent1().key());
                    } catch (NoSuchEventException e) {
                        throw HaltCheckerException.error("The read or write event is not found.");
                    }
                }
                ExecutionGraphNode e2 = null;
                if (item.getEvent2() != null) {
                    try {
                        e2 = cln.getEventNode(item.getEvent2().key());
                    } catch (NoSuchEventException e) {
                        throw HaltCheckerException.error("The read or write event is not found.");
                    }
                }
                ExplorationStack.Item clone = ExplorationStack.Item.makeItem(item.getType(), e1, e2, cln);
                for (Event e : item.getAdditionalEventsToProcess()) {
                    clone.addAdditionalEvent(e);
                }
                clones.add(clone);
            } else {
                clones.add(item);
            }
        }
        return clones;
    }

    public void updateStack(Algo alg) {
        // Try to explore the next frontier. If we cannot explore the next frontier,
        // it means we have explored all the reachable nodes
        if (!exploreNextFrontier(alg)) {
            // We have explored all nodes in the current frontier, we can update the frontier with the next frontier
            // and continue the exploration.
            updateEstimation();
            updateProd();
            updateFrontier();
            exploreNextFrontier(alg);
        }
    }

    public boolean isDone() {
        return current.isEmpty() && next.isEmpty();
    }

    /**
     *
     */
    @Override
    public void resetReExecutionFlag() {
        reExecutionNeeded = false;
    }

    /**
     * @return
     */
    @Override
    public boolean isReExecutionNeeded() {
        return reExecutionNeeded;
    }

    /**
     * @return
     */
    @Override
    public int getExpectedValue() {
        return (int) (getRealExpectedValue());
    }

    public float getRealExpectedValue() {
        updateEstimation();
        return expectedValue;
    }

    /**
     *
     */
    @Override
    public void reset() {
        expectedValue = 0.0f;
        prod = 1.0f;
        current.clear();
        currentLeaves.clear();
        ExplorationStack.Item dummy = ExplorationStack.Item.continueCurrent();
        currentLeaves.put(dummy, true);
        currentItem = dummy;
        next.clear();
        resetReExecutionFlag();
    }
}
