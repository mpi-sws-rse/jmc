package org.mpisws.jmc.strategies.estimation.trust.wgTrust;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.jmc.strategies.estimation.trust.TrustEstimator;
import org.mpisws.jmc.strategies.trust.Algo;
import org.mpisws.jmc.strategies.trust.ExplorationStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.random.RandomGeneratorFactory;

public class WgTrustEstimator extends TrustEstimator {

    private static final Logger LOGGER = LogManager.getLogger(WgTrustEstimator.class);

    private final int FWR_WEIGHT = 7;

    private final int BWR_WEIGHT = 2;

    protected ExplorationStack.Item pickNextOption(List<ExplorationStack.Item> items, ExplorationStack stack, Algo alg) {
        if (!hasBackwardRevisit(items)) {
            // Then everything is a forward revisits, we can pick any of them uniformly
            int index = RandomGeneratorFactory.of("Xoshiro256PlusPlus").create().nextInt(items.size());
            return items.get(index);
        }

        List<Integer> weights = new ArrayList<>();
        for (ExplorationStack.Item item : items) {
            weights.add(item.isBackwardRevisit() ? BWR_WEIGHT : FWR_WEIGHT);
        }
        int[] cumulativeWeights = new int[items.size()];

        int sum = 0;
        int totalWeight;
        for (int i = 0; i < weights.size(); i++) {
            sum += weights.get(i);
            cumulativeWeights[i] = sum;
        }
        totalWeight = sum;
        int r = RandomGeneratorFactory.of("Xoshiro256PlusPlus").create().nextInt(totalWeight);
        int index = Arrays.binarySearch(cumulativeWeights, r);
        if (index < 0) index = -index - 1;
        return items.get(index);
    }

    private boolean hasBackwardRevisit(List<ExplorationStack.Item> items) {
        // Check if there exist a BWR items among the list
        for (ExplorationStack.Item item : items) {
            if (item.isBackwardRevisit()) {
                return true;
            }
        }
        return false;
    }
}
