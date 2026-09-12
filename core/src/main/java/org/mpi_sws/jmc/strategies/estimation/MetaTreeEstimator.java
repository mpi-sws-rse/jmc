package org.mpi_sws.jmc.strategies.estimation;

import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.strategies.trust.Algo;

/**
 * The estimator contract for the <b>trust</b> family (Trust, Weighted-Trust, and TeStor estimation).
 *
 * <p>A tree estimator samples the tree of TruSt {@code D(P)} — the tree whose leaves are the maximal
 * execution graphs — by reading the branching structure from TruSt's exploration stack via the
 * {@link Algo} driver. Because a randomly chosen child need not follow TruSt's DFS order, the
 * estimator signals when the program must be <b>re-executed</b> (guided by the chosen child's graph)
 * to continue the walk. Implemented by {@code TrustEstimator}, {@code WgTrustEstimator}, and {@code
 * Testor}.
 */
public interface MetaTreeEstimator {

    /**
     * Samples the branching at the current node of {@code D(P)} using the algorithm driver's
     * exploration stack, updating the running estimate and possibly requesting a re-execution.
     *
     * @param alg the TruSt algorithm driver holding the current graph and exploration stack.
     * @throws HaltTaskException if the current task must halt.
     * @throws HaltExecutionException if the whole execution must halt.
     */
    void updateTree(Algo alg) throws HaltTaskException, HaltExecutionException;

    /** Clears the re-execution request (called before each iteration). */
    void resetReExecutionFlag();

    /**
     * Returns whether the estimator needs the program re-executed (guided) to reach the sampled
     * node.
     *
     * @return true if a re-execution is required.
     */
    boolean isReExecutionNeeded();

    /**
     * Returns the current trial's point estimate.
     *
     * @return the estimate.
     */
    int getExpectedValue();

    /** Resets the estimator to start a fresh trial. */
    void reset();
}
