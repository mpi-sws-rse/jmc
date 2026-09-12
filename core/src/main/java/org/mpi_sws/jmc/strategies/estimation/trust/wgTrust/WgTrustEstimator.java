package org.mpi_sws.jmc.strategies.estimation.trust.wgTrust;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.strategies.estimation.trust.TrustEstimator;
import org.mpi_sws.jmc.strategies.trust.Algo;
import org.mpi_sws.jmc.strategies.trust.ExplorationStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.random.RandomGeneratorFactory;

/**
 * Weighted variant of {@link TrustEstimator} (Algorithm T) — the estimator behind the {@code
 * wg-trust-estimation} strategy.
 *
 * <p>It samples the tree of TruSt exactly like {@link TrustEstimator}, but overrides child selection
 * ({@link #pickNextOption}) to choose <em>non-uniformly</em>: forward revisits get weight {@link
 * #FWR_WEIGHT} and backward revisits weight {@link #BWR_WEIGHT}. The intent is to counteract tree
 * imbalance — forward revisits tend to lead to shallow subtrees and backward revisits to deep ones —
 * and thereby reduce estimation variance.
 *
 * <p><b>TODO (known bias — to revisit together in future):</b> weighting changes the sampling
 * distribution, but this class still inherits {@link TrustEstimator}'s estimate update
 * ({@code expectedValue *= number of children}) with <em>no importance-sampling correction</em> (it
 * does not divide by the per-node selection probability). As written, the weighted estimator is
 * therefore <em>biased</em>: its expected value is not {@code C(P)} in general, unlike uniform
 * Algorithm T. The idea was that re-weighting forward/backward revisits would fix tree-imbalance and
 * cut variance, but the balancing/correction was not fully worked out and something may have been
 * missed. For now this is treated as an <em>intentionally biased</em> heuristic; deriving a correct
 * weighted (importance-sampling) estimator is future work.
 *
 * <p><b>TODO (unseeded randomness — to revisit together in future):</b> {@link #pickNextOption}
 * creates a fresh {@code Xoshiro256PlusPlus} generator on every call instead of drawing from the
 * strategy's configured seed, so the weighted sampling is not reproducible from the seed. This
 * should use a single seeded generator so runs are deterministic given a seed.
 */
public class WgTrustEstimator extends TrustEstimator {

    private static final Logger LOGGER = LogManager.getLogger(WgTrustEstimator.class);

    /** Selection weight of a forward revisit (and the continue-current child). */
    private final int FWR_WEIGHT = 3;

    /** Selection weight of a backward revisit. */
    private final int BWR_WEIGHT = 1;

    /**
     * Picks one child by <em>weighted</em> random selection (forward {@link #FWR_WEIGHT}, backward
     * {@link #BWR_WEIGHT}) when the children include a backward revisit; otherwise falls back to a
     * uniform pick. Overrides {@link TrustEstimator#pickNextOption}.
     *
     * <p>See the class header: the estimate update is inherited unchanged, so weighted selection
     * makes this estimator biased (a known {@code TODO}).
     *
     * @param items the children of the current node.
     * @param stack the exploration stack (unused).
     * @param alg the algorithm driver (unused).
     * @return the chosen child.
     */
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

    /**
     * Returns whether any child is a backward revisit (weighted selection is only applied then).
     *
     * @param items the children of the current node.
     * @return true if a backward revisit is present.
     */
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
