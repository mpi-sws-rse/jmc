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

public class TrustEstimator implements MetaTreeEstimator {

    private static final Logger LOGGER = LogManager.getLogger(TrustEstimator.class);

    protected boolean reExecutionNeeded = false;

    protected int expectedValue = 1;

    protected final StringBuilder treeLogger = new StringBuilder().append("$Iteration_0").append(System.lineSeparator());

    protected long graphId = 1L;

    protected long graphCounter = 1L;

    protected Map<ExplorationStack.Item, Long> nextGraphIds = new HashMap<>();

    /**
     * @param alg
     * @throws HaltTaskException
     * @throws HaltExecutionException
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

    private List<ExplorationStack.Item> getAllItems(ExplorationStack stack) {
        List<ExplorationStack.Item> items = new ArrayList<>();
        while (!stack.isEmpty()) {
            items.add(stack.pop());
        }
        return items;
    }

    protected ExplorationStack.Item pickNextOption(List<ExplorationStack.Item> items, ExplorationStack stack, Algo alg) {
        // Pick a random int value between 0 and items.size() (both inclusive)
        int randomIndex = RandomGeneratorFactory.of("Xoshiro256PlusPlus").create().nextInt(items.size());
        return items.get(randomIndex);
    }

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

    private void updateGraphId(ExplorationStack.Item item) {
        graphId = nextGraphIds.get(item);
    }

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

    public void resetReExecutionFlag() {
        reExecutionNeeded = false;
    }

    public boolean isReExecutionNeeded() {
        return reExecutionNeeded;
    }

    /**
     * @return
     */
    public int getExpectedValue() {
        return expectedValue;
    }

    /**
     *
     */
    public void reset() {
        expectedValue = 1;
        resetReExecutionFlag();
        treeLogger.setLength(0);
        graphCounter = 1L;
        graphId = 1L;
        nextGraphIds.clear();
    }

    public StringBuilder getTreeLogger() {
        return treeLogger;
    }

    public void resetTreeLogger() {
        treeLogger.setLength(0);
    }
}
