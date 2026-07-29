package org.mpi_sws.jmc.solver;

/**
 * The outcome of evaluating a symbolic constraint for ConDpor.
 *
 * <p>Returned by the solver's {@code computeNewSymbolicOperation}: {@link #result} is the branch
 * taken now (computed from the concrete model, no solver call), and {@link #isNegatable} says
 * whether the opposite branch is also satisfiable (so the exploration must branch). It is carried on
 * symbolic scheduling choices and re-applied during guided re-execution.
 */
public class SolverResult {

    /** The boolean outcome of the constraint under the current concrete model. */
    private final boolean result;
    /** Whether the negation of the constraint is also satisfiable (the other branch is feasible). */
    private final boolean isNegatable;

    /**
     * Creates a solver result.
     *
     * @param result the branch taken under the current model.
     * @param isNegatable whether the other branch is also feasible.
     */
    public SolverResult(boolean result, boolean isNegatable) {
        this.result = result;
        this.isNegatable = isNegatable;
    }

    /**
     * Returns the branch taken under the current model.
     *
     * @return the boolean outcome.
     */
    public boolean result() {
        return result;
    }

    /**
     * Returns whether the other branch is also feasible.
     *
     * @return true if the constraint is negatable.
     */
    public boolean isNegatable() {
        return isNegatable;
    }
}
