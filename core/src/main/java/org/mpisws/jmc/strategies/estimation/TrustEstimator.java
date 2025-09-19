package org.mpisws.jmc.strategies.estimation;

import org.mpisws.jmc.runtime.HaltExecutionException;
import org.mpisws.jmc.runtime.HaltTaskException;
import org.mpisws.jmc.strategies.trust.Algo;
import org.mpisws.jmc.strategies.trust.ExplorationStack;

import java.util.ArrayList;
import java.util.List;

public class TrustEstimator implements Estimator {

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
        expectedValue = expectedValue * size + 1;
        pickNextOption(items, stack);
    }

    private List<ExplorationStack.Item> getAllItems(ExplorationStack stack) {
        List<ExplorationStack.Item> items = new ArrayList<>();
        while (!stack.isEmpty()) {
            items.add(stack.pop());
        }
        return items;
    }

    private void pickNextOption(List<ExplorationStack.Item> items, ExplorationStack stack) {
        // Pick a random int value between 0 and items.size() (both inclusive)
        int randomIndex = (int) (Math.random() * items.size());
        if (randomIndex == items.size()) {
            // Do nothing, this means we are continuing the current execution
            return;
        }
        ExplorationStack.Item item = items.get(randomIndex);
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
    }
}
