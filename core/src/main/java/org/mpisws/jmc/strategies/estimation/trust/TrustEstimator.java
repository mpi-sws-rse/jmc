package org.mpisws.jmc.strategies.estimation.trust;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.jmc.runtime.HaltExecutionException;
import org.mpisws.jmc.runtime.HaltTaskException;
import org.mpisws.jmc.strategies.estimation.Estimator;
import org.mpisws.jmc.strategies.trust.Algo;
import org.mpisws.jmc.strategies.trust.ExplorationStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TrustEstimator implements Estimator {

    private static final Logger LOGGER = LogManager.getLogger(TrustEstimator.class);

    private boolean reExecutionNeeded = false;

    private int expectedValue = 1;

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

        int size = items.size();
        expectedValue = (expectedValue * (size + 1));
        pickNextOption(items, stack, alg);
    }

    private List<ExplorationStack.Item> getAllItems(ExplorationStack stack) {
        List<ExplorationStack.Item> items = new ArrayList<>();
        while (!stack.isEmpty()) {
            items.add(stack.pop());
        }
        return items;
    }

    private void pickNextOption(List<ExplorationStack.Item> items, ExplorationStack stack, Algo alg) {
        // Pick a random int value between 0 and items.size() (both inclusive)
        Random random = new Random();
        int randomIndex = random.nextInt(items.size() + 1);
        if (randomIndex == items.size()) {
            // Do nothing, this means we are continuing the current execution
            return;
        }
        ExplorationStack.Item item = items.get(randomIndex);
        if (item.isBackwardRevisit()) {
            // If the next item is a backward revisit, we need to process it and then update the tree again
            // if the stack size is greater than 1, otherwise we need to re-execute
            LOGGER.debug("Revisiting a backward choice");
            alg.processBWR(item);
            if (alg.getExplorationStack().size() > 1) {
                updateTreeBW(alg);
            } else {
                reExecutionNeeded = true;
            }
        } else {
            stack.push(item);
            reExecutionNeeded = true;
        }
    }

    private void updateTreeBW(Algo alg) throws HaltTaskException, HaltExecutionException {
        ExplorationStack stack = alg.getExplorationStack();
        List<ExplorationStack.Item> items = getAllItems(stack);
        if (items.isEmpty() || items.size() < 2) {
            throw HaltExecutionException.error("The number of items in the stack is less than 2");
        }

        int size = items.size();
        expectedValue = (expectedValue * (size));
        pickNextOptionBW(items, stack, alg);
    }

    private void pickNextOptionBW(List<ExplorationStack.Item> items, ExplorationStack stack, Algo alg) {
        Random random = new Random();
        int randomIndex = random.nextInt(items.size());
        ExplorationStack.Item item = items.get(randomIndex);
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
    }
}
