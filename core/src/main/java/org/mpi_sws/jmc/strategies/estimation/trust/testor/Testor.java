package org.mpi_sws.jmc.strategies.estimation.trust.testor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.runtime.HaltCheckerException;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.strategies.estimation.MetaTreeEstimator;
import org.mpi_sws.jmc.strategies.trust.*;

import java.util.*;

/**
 * Estimator implementing **Algorithm S** (stochastic enumeration) — the {@code TeStor} estimator,
 * the most advanced and accurate estimator in JMC — for the {@code testor} strategy.
 *
 * <p>Driven by {@link org.mpi_sws.jmc.strategies.estimation.trust.testor.TestorStrategy}, it keeps a
 * <em>frontier</em> of up to {@link #budget} nodes of the tree of TruSt {@code D(P)}. Sequentially it
 * finds the children of every frontier node (each reached by a guided re-execution), accumulates the
 * per-level leaf contribution into {@link #expectedValue} weighted by the running branching product
 * {@link #prod}, then resamples a random size-{@code budget} subset of the successors as the next
 * frontier ({@link #randomSelection}) and continues. Budget {@code 1} reduces to Algorithm T. The
 * coupling of the frontier is what reduces variance relative to independent walks.
 *
 * <p><b>TODO (unseeded randomness — to revisit together in future):</b> {@link #randomSelection}
 * uses {@link Math#random()} rather than the strategy's configured seed, so the sampling is not
 * reproducible from the seed. This should use a single seeded generator so runs are deterministic
 * given a seed.
 */
public class Testor implements MetaTreeEstimator {

    private static final Logger LOGGER = LogManager.getLogger(Testor.class);

    /** Maximum number of frontier nodes kept coupled at each level ({@code budget 1} = Algorithm T). */
    private final int budget;

    /** Set when the sampled frontier node requires a guided re-execution to be reached. */
    protected boolean reExecutionNeeded = false;

    /** The accumulated estimate {@code Σ prod · (leaves/frontier)}; starts at 0. */
    protected float expectedValue = 0.0f;

    /** The running branching-ratio product {@code ∏ |successors|/|frontier|}; starts at 1. */
    protected float prod = 1.0f;

    /** The current frontier's nodes still to be expanded this level. */
    private final List<ExplorationStack.Item> current = new ArrayList<>();

    /** Maps each current-frontier node to whether it is (so far) a leaf of {@code D(P)}. */
    private final Map<ExplorationStack.Item, Boolean> currentLeaves = new HashMap<>();

    /** Accumulates all successors of the current frontier before resampling the next one. */
    private final List<ExplorationStack.Item> next = new ArrayList<>();

    /** The frontier node currently being expanded. */
    private ExplorationStack.Item currentItem;

    /**
     * Creates a TeStor with the given frontier budget, seeded with a single dummy leaf (the root).
     *
     * @param budget the maximum frontier size.
     */
    public Testor(int budget) {
        this.budget = budget;
        ExplorationStack.Item dummy = ExplorationStack.Item.continueCurrent();
        currentLeaves.put(dummy, true);
        currentItem = dummy;
    }

    /** Creates a TeStor with the default frontier budget of 2. */
    public Testor() {
        this(2);
    }

    /**
     * Expands the current frontier node: collects its children (each a guided re-execution target),
     * marks the node non-leaf, appends the sc-max continuation child (for estimation only),
     * accumulates the children into the next frontier, and advances via {@link #updateStack}.
     *
     * <p>Does nothing while the algorithm is guiding a re-execution.
     *
     * @param alg the TruSt algorithm driver.
     * @throws HaltTaskException if the current task must halt.
     * @throws HaltExecutionException if the whole execution must halt.
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

    /**
     * Backward-revisit variant of {@link #updateTree}: after a backward revisit pushed its coherence
     * placements, collects them as children, appends the internally-handled {@code FLW} continuation
     * (needed because exploration is not DFS), accumulates them, and advances.
     *
     * @param alg the algorithm driver.
     * @throws HaltTaskException if the current task must halt.
     * @throws HaltExecutionException if the whole execution must halt.
     */
    private void updateTreeBW(Algo alg) throws HaltTaskException, HaltExecutionException {
        List<ExplorationStack.Item> items = retrieveItems(alg);
        if (items.isEmpty() || items.size() < 1) {
            throw HaltExecutionException.error("The number of items in the stack is less than 1");
        }

        // Update the leaves map for the current frontier.
        updateLeaves();

        // Add a node representing the FLW which we handled internally in order to fix the co-edge (This is needed
        // since we are not exploring the tree of trust in the dfs manner).
        appendCurrentItem(items, alg);

        // Accumulate the reachable nodes into the next frontier
        updateNext(items);
        items = null; // Help GC

        updateStack(alg);
    }

    /** Marks the current frontier node as an internal (non-leaf) node, since it has children. */
    private void updateLeaves() {
        if (currentItem != null && currentLeaves.containsKey(currentItem)) {
            currentLeaves.put(currentItem, false);
        }
    }

    /**
     * Pops and returns every pending item from the exploration stack (the node's children).
     *
     * @param alg the algorithm driver.
     * @return the popped items.
     */
    private List<ExplorationStack.Item> retrieveItems(Algo alg) {
        ExplorationStack stack = alg.getExplorationStack();
        List<ExplorationStack.Item> items = new ArrayList<>();
        while (!stack.isEmpty()) {
            items.add(stack.pop());
        }
        return items;
    }

    /**
     * Appends a "continue current" child carrying the current execution graph — the sc-max
     * continuation, which is counted for estimation but not itself enumerated.
     *
     * @param items the child list to append to.
     * @param alg the algorithm driver.
     * @return true if a child was appended.
     */
    private boolean appendCurrentItem(List<ExplorationStack.Item> items, Algo alg) {
        if (items.isEmpty() || alg == null || alg.getExecutionGraph() == null) {
            return false;
        }
        ExplorationStack.Item currItem = ExplorationStack.Item.continueCurrent(alg.getExecutionGraph());
        items.add(currItem);
        return true;
    }

    /**
     * Accumulates the given children into the next-frontier buffer.
     *
     * @param items the children to add.
     * @return true if anything was added.
     */
    private boolean updateNext(List<ExplorationStack.Item> items) {
        if (items.isEmpty() || next == null) {
            return false;
        }
        next.addAll(items);
        items.clear();
        items = null; // Help GC
        return true;
    }

    /**
     * Resamples the next frontier once the current one is fully expanded: randomly selects at most
     * {@link #budget} successors ({@link #randomSelection}), clones them ({@link #makeClone}), and
     * makes them the new current frontier with all nodes provisionally marked as leaves.
     *
     * @return true if a new frontier was formed.
     */
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

    /** Multiplies the running product by {@code |successors|/|frontier|} for the level just expanded. */
    private void updateProd() {
        float d = (float) next.size() / (float) countFrontier();
        prod = prod * d;
    }

    /** Adds this level's leaf contribution {@code prod · (leaves/frontier)} to the estimate. */
    private void updateEstimation() {
        expectedValue = expectedValue + (prod * ((float) countLeaves() / (float) countFrontier()));
    }

    /** Returns the size of the current frontier (number of coupled nodes). */
    private int countFrontier() {
        return currentLeaves.size();
    }

    /**
     * Counts the current frontier's leaves — nodes still marked as leaves whose graph is a complete,
     * consistent execution graph (a genuine maximal graph of {@code P}).
     *
     * @return the number of leaves.
     */
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

    /**
     * Removes and returns the next node to expand from the current frontier.
     *
     * @return the next node, or {@code null} if the current frontier is empty.
     */
    private ExplorationStack.Item getNextFrontier() {
        if (current.isEmpty()) {
            return null;
        }
        return current.remove(0);
    }

    /**
     * Takes the next current-frontier node and dispatches it — backward revisits via {@link
     * #handleBWR}, everything else via {@link #handleNonBWR}.
     *
     * @param alg the algorithm driver.
     * @return true if a node was expanded; false if the current frontier is exhausted.
     */
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

    /**
     * Handles a backward-revisit frontier node: processes it, then either recurses on its coherence
     * placements ({@link #updateTreeBW}, after {@link #fixCoEdge}) or, if it left a single successor,
     * promotes that successor to a leaf and handles it as a non-backward node.
     *
     * @param alg the algorithm driver.
     * @param nextItem the backward-revisit node.
     * @return true.
     */
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

    /**
     * Handles a non-backward-revisit frontier node: pushes it back onto the stack, makes it the
     * current node, and flags a guided re-execution to reach it.
     *
     * @param alg the algorithm driver.
     * @param nextItem the node to reach.
     * @return true.
     */
    private boolean handleNonBWR(Algo alg, ExplorationStack.Item nextItem) {
        ExplorationStack stack = alg.getExplorationStack();
        stack.push(nextItem);
        currentItem = nextItem;
        reExecutionNeeded = true;
        return true;
    }

    /**
     * Places the coherence ({@code FLW}) of the pending last-write revisit on top of the stack so the
     * graph is well-formed before continuing (needed because exploration is not DFS).
     *
     * @param alg the algorithm driver.
     */
    private void fixCoEdge(Algo alg) {
        ExplorationStack.Item top = alg.getExplorationStack().pop();
        if (!top.isLastWriteRevisit()) {
            throw HaltCheckerException.error("The top item in the stack is not a last write revisit.");
        }
        alg.setExecutionGraph(top.getGraph());
        alg.processFLW(top);

        top = null; // Help GC
    }

    /**
     * Selects at most {@link #budget} distinct items uniformly at random via a Fisher-Yates partial
     * shuffle; returns all items if there are no more than {@code budget}.
     *
     * <p>See the class {@code TODO}: this uses {@link Math#random()}, so selection is not seed-reproducible.
     *
     * @param items the candidate successors.
     * @return the sampled subset (size ≤ {@code budget}).
     */
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

    /**
     * Deep-copies the selected frontier items so each carries its own execution graph (re-resolving
     * event nodes against the clone). Backward-revisit items are kept as-is (they reference the live
     * graph they will revisit).
     *
     * @param items the selected successors.
     * @return the cloned items.
     */
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

    /**
     * Advances exploration: expands the next node of the current frontier; when the current frontier
     * is exhausted, folds in this level's estimate ({@link #updateEstimation}), updates the product
     * ({@link #updateProd}), resamples the next frontier ({@link #updateFrontier}), and starts it.
     *
     * @param alg the algorithm driver.
     */
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

    /**
     * Returns whether the trial is finished — both the current and next frontiers are empty.
     *
     * @return true if exploration is complete.
     */
    public boolean isDone() {
        return current.isEmpty() && next.isEmpty();
    }

    /** Clears the re-execution request. */
    @Override
    public void resetReExecutionFlag() {
        reExecutionNeeded = false;
    }

    /**
     * Returns whether a guided re-execution is required to reach the current frontier node.
     *
     * @return true if re-execution is needed.
     */
    @Override
    public boolean isReExecutionNeeded() {
        return reExecutionNeeded;
    }

    /**
     * Returns the trial's point estimate truncated to an int (see {@link #getRealExpectedValue}).
     *
     * @return the estimate as an int.
     */
    @Override
    public int getExpectedValue() {
        return (int) (getRealExpectedValue());
    }

    /**
     * Returns the trial's point estimate as a float, folding in the current (final) level's leaf
     * contribution first.
     *
     * @return the accumulated estimate.
     */
    public float getRealExpectedValue() {
        updateEstimation();
        return expectedValue;
    }

    /** Resets the estimate, product, frontiers, and re-execution flag (re-seeding the root leaf) for a fresh trial. */
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
