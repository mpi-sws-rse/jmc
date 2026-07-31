package org.mpi_sws.jmc.strategies.estimation.trust;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.strategies.estimation.MetaTreeEstimator;
import org.mpi_sws.jmc.strategies.trust.Algo;
import org.mpi_sws.jmc.strategies.trust.EventUtils;
import org.mpi_sws.jmc.strategies.trust.ExplorationStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGeneratorFactory;

/**
 * Estimator implementing **Algorithm T** — Knuth's estimator over the tree of TruSt {@code D(P)} —
 * for the {@code trust-estimation} strategy.
 *
 * <p>Driven by {@link org.mpi_sws.jmc.strategies.estimation.trust.TrustEstimationStrategy}, it runs
 * TruSt but, at each branching node of {@code D(P)}, collects the pending revisits plus a
 * "continue current" child, multiplies the running product {@link #expectedValue} by the number of
 * children (Knuth's branching factor), and picks one child uniformly at random ({@link
 * #pickNextOption}). When the chosen child does not follow DFS order it sets {@link
 * #reExecutionNeeded} so the strategy re-executes the program guided by that child's graph. One
 * root-to-leaf walk yields one unbiased point estimate of {@code C(P)}; the mean over trials
 * estimates {@code C(P)}.
 *
 * <p><b>TODO (unseeded randomness — to revisit together in future):</b> {@link #pickNextOption} (and
 * {@link #pickNextOptionBW}) creates a fresh {@code Xoshiro256PlusPlus} generator on every call
 * instead of drawing from the strategy's configured seed, so the sampling is not reproducible from
 * the seed. This should use a single seeded generator so runs are deterministic given a seed.
 */
public class TrustEstimator implements MetaTreeEstimator {

    private static final Logger LOGGER = LogManager.getLogger(TrustEstimator.class);

    /** Set when the sampled child requires a guided re-execution to be reached. */
    protected boolean reExecutionNeeded = false;

    /** The running product estimate (Knuth's {@code ∏ branchingFactor}) for the current walk. */
    protected int expectedValue = 1;

    /** Diagnostic text rendering of the sampled tree path (children marked {@code F}/{@code B}). */
    protected final StringBuilder treeLogger = new StringBuilder();

    /** Id of the tree node currently being expanded (for the tree log). */
    protected long graphId = 1L;

    /** Monotonic counter assigning ids to child nodes (for the tree log). */
    protected long graphCounter = 1L;

    /** Maps a pending child item to the tree-log id reserved for it. */
    protected Map<ExplorationStack.Item, Long> nextGraphIds = new HashMap<>();

    /**
     * Samples the branching at the current node of {@code D(P)}.
     *
     * <p>Skips if the algorithm is guiding a re-execution. Otherwise it pops all pending revisits off
     * the exploration stack, appends a {@code continueCurrent} child (the forward continuation),
     * multiplies {@link #expectedValue} by the number of children (Knuth's branching factor), picks
     * one child uniformly at random, and follows it — continuing in place, or flagging re-execution
     * for revisits that break DFS order.
     *
     * @param alg the TruSt algorithm driver.
     * @throws HaltTaskException if the current task must halt.
     * @throws HaltExecutionException if the whole execution must halt.
     */
    public void updateTree(Algo alg) throws HaltTaskException, HaltExecutionException {
        if (alg.areWeGuiding()) {
            return;
        }

        ExplorationStack stack = alg.getExplorationStack();
        List<ExplorationStack.Item> items = getAllItems(stack);
        if (items.isEmpty()) {
            return;
        }

        // Create an item for continuing the current execution
        ExplorationStack.Item currItem = ExplorationStack.Item.continueCurrent();
        items.add(currItem);
        updateTreeLogger(items);
        int size = items.size();
        expectedValue = (expectedValue * (size));
        ExplorationStack.Item nextItem = pickNextOption(items, stack, alg);
        updateGraphId(nextItem);
        handleNextItem(nextItem, stack, alg);
        nextGraphIds.clear();
    }

    /**
     * Appends one line to the tree log recording the current node's children (each marked {@code B}
     * for a backward revisit or {@code F} otherwise) and reserving their ids.
     *
     * @param items the children of the current node.
     */
    private void updateTreeLogger(List<ExplorationStack.Item> items) {
        treeLogger.append(graphId).append(" -> ");
        for (int i = 0; i < items.size(); i++) {
            graphCounter++;
            nextGraphIds.put(items.get(i), graphCounter);
            treeLogger.append(graphCounter).append("(")
                    .append(items.get(i).isBackwardRevisit() ? "B" : "F")
                    .append(")");
            if (i < items.size() - 1) {
                treeLogger.append(", ");
            }
        }
        treeLogger.append(System.lineSeparator());
    }

    /**
     * Pops and returns every pending item from the exploration stack (the current node's revisits).
     *
     * @param stack the exploration stack.
     * @return the popped items.
     */
    private List<ExplorationStack.Item> getAllItems(ExplorationStack stack) {
        List<ExplorationStack.Item> items = new ArrayList<>();
        while (!stack.isEmpty()) {
            items.add(stack.pop());
        }
        return items;
    }

    /**
     * Picks one child uniformly at random (overridden by {@code WgTrustEstimator} for weighted
     * selection).
     *
     * @param items the children of the current node.
     * @param stack the exploration stack (unused here).
     * @param alg the algorithm driver (unused here).
     * @return the chosen child.
     */
    protected ExplorationStack.Item pickNextOption(List<ExplorationStack.Item> items, ExplorationStack stack, Algo alg) {
        // Pick a random int value between 0 and items.size() (both inclusive)
        int randomIndex = RandomGeneratorFactory.of("Xoshiro256PlusPlus").create().nextInt(items.size());
        return items.get(randomIndex);
    }

    /**
     * Follows the chosen child: continue-current does nothing (DFS continues); a backward revisit is
     * processed (recursing over its coherence placements if it pushed more items, else flagging
     * re-execution); a forward revisit is pushed back and flags re-execution.
     *
     * @param item the chosen child.
     * @param stack the exploration stack.
     * @param alg the algorithm driver.
     * @throws HaltTaskException if the current task must halt.
     * @throws HaltExecutionException if the whole execution must halt.
     */
    protected void handleNextItem(ExplorationStack.Item item, ExplorationStack stack, Algo alg) {
        if (item.isContinueCurrent()) {
            // Do nothing, this means we are continuing the current execution
            return;
        }

        if (item.isBackwardRevisit()) {
            // If the next item is a backward revisit, we need to process it and then update the tree again
            // if the stack size is greater than 1, otherwise we need to re-execute
            LOGGER.debug("Revisiting a backward choice");
            alg.processBWR(item);
            if (alg.getExplorationStack().size() > 1) {
                updateTreeBW(alg);
            } else {
                ExplorationStack.Item topItem = alg.getExplorationStack().peek();
                updateTreeLogger(List.of(topItem));
                updateGraphId(topItem);
                reExecutionNeeded = true;
            }
        } else {
            updateLoggerForRdx(item);
            stack.push(item);
            reExecutionNeeded = true;
        }
    }

    /**
     * Adds tree-log entries for a lock-acquire-read (RDX) forward revisit, which corresponds to a
     * backward followed by a forward step in the lock-handling of the tree of TruSt.
     *
     * @param item the chosen forward-revisit item.
     */
    private void updateLoggerForRdx(ExplorationStack.Item item) {
        if (!EventUtils.isLockAcquireRead(item.getEvent1().getEvent())) {
            return;
        }
        graphCounter++;
        treeLogger.append(graphId).append(" -> ").append(graphCounter).append("(B)").append(System.lineSeparator());
        graphId = graphCounter;
        graphCounter++;
        treeLogger.append(graphId).append(" -> ").append(graphCounter).append("(F)").append(System.lineSeparator());
        graphId = graphCounter;
    }

    /**
     * Advances the tree-log "current node" id to the one reserved for the chosen item.
     *
     * @param item the chosen child.
     */
    private void updateGraphId(ExplorationStack.Item item) {
        graphId = nextGraphIds.get(item);
    }

    /**
     * Samples the next branching after a backward revisit that pushed its coherence placements
     * ({@code FWW}/{@code FLW}): counts them as children, multiplies the estimate, and picks one.
     *
     * @param alg the algorithm driver.
     * @throws HaltTaskException if the current task must halt.
     * @throws HaltExecutionException if the whole execution must halt.
     */
    private void updateTreeBW(Algo alg) throws HaltTaskException, HaltExecutionException {
        ExplorationStack stack = alg.getExplorationStack();
        List<ExplorationStack.Item> items = getAllItems(stack);
        if (items.isEmpty() || items.size() < 2) {
            throw HaltExecutionException.error("The number of items in the stack is less than 2");
        }

        updateTreeLogger(items);
        int size = items.size();
        expectedValue = (expectedValue * (size));
        pickNextOptionBW(items, stack, alg);
    }

    /**
     * Picks one of the post-backward-revisit placements uniformly at random; for a non-{@code FLW}
     * pick it first places the coherence ({@code processFLW}) so the graph is well-formed, then
     * pushes it and flags re-execution.
     *
     * @param items the placement children.
     * @param stack the exploration stack.
     * @param alg the algorithm driver.
     */
    private void pickNextOptionBW(List<ExplorationStack.Item> items, ExplorationStack stack, Algo alg) {
        int randomIndex = RandomGeneratorFactory.of("Xoshiro256PlusPlus").create().nextInt(items.size());
        ExplorationStack.Item item = items.get(randomIndex);
        updateGraphId(item);
        if (item.getType() != ExplorationStack.ItemType.FLW) {
            // If the next item is not a FLW, we need to track coherency for the event1 of the item
            // Otherwise, the swapCoherency will break, since the FLW event is not processed
            alg.setExecutionGraph(item.getGraph());
            alg.processFLW(item);
        }
        stack.push(item);
        reExecutionNeeded = true;
    }

    /** Clears the re-execution request. */
    public void resetReExecutionFlag() {
        reExecutionNeeded = false;
    }

    /**
     * Returns whether a guided re-execution is required to reach the sampled node.
     *
     * @return true if re-execution is needed.
     */
    public boolean isReExecutionNeeded() {
        return reExecutionNeeded;
    }

    /**
     * Returns the current walk's point estimate.
     *
     * @return the running branching-factor product.
     */
    public int getExpectedValue() {
        return expectedValue;
    }

    /** Resets the estimate, re-execution flag, and tree-log state for a fresh trial. */
    public void reset() {
        expectedValue = 1;
        resetReExecutionFlag();
        treeLogger.setLength(0);
        graphCounter = 1L;
        graphId = 1L;
        nextGraphIds.clear();
    }

    /**
     * Returns the diagnostic tree log built for the current trial.
     *
     * @return the tree-log buffer.
     */
    public StringBuilder getTreeLogger() {
        return treeLogger;
    }

    /** Clears the tree log. */
    public void resetTreeLogger() {
        treeLogger.setLength(0);
    }
}
