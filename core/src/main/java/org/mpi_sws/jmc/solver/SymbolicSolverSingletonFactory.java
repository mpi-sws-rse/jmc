package org.mpi_sws.jmc.solver;

import org.mpi_sws.jmc.solver.incremental.IncrementalSolver;

/**
 * Holds the single process-wide {@link SymbolicSolver} instance and lazily creates it.
 *
 * <p>Creating a JavaSMT solver context is expensive, so JMC keeps exactly one (an {@link
 * IncrementalSolver}). The first call with a solver type creates it; later calls return the same
 * instance. Accessed through {@link SolverUtil}.
 */
public class SymbolicSolverSingletonFactory {

    /** The single solver instance, or {@code null} until first created. */
    private static SymbolicSolver solver;

    /**
     * Returns the singleton solver, creating it with the given type on first use.
     *
     * @param solverType the SMT backend (required only on first creation).
     * @return the singleton solver.
     * @throws IllegalStateException if no solver exists and {@code solverType} is {@code null}.
     */
    public static SymbolicSolver getSolver(SMTSolverTypes solverType) {
        if (solver != null) {
            return solver;
        }

        if (solverType == null) {
            throw new IllegalStateException("Solver type must be provided when creating a new SymbolicSolver");
        } else {
            solver = new IncrementalSolver(solverType);
        }
        return solver;
    }

    /**
     * Returns the singleton as an {@link IncrementalSolver}, creating it with the given type on first
     * use.
     *
     * @param solverType the SMT backend (required only on first creation).
     * @return the singleton incremental solver.
     * @throws IllegalStateException if no solver exists and {@code solverType} is {@code null}, or if
     *     the existing singleton is not an {@code IncrementalSolver}.
     */
    public static IncrementalSolver getIncrementalSolver(SMTSolverTypes solverType) {
        if (solver != null) {
            if (solver instanceof IncrementalSolver incrementalSolver) {
                return incrementalSolver;
            }
            throw new IllegalStateException("Solver singleton is not an IncrementalSolver");
        }

        if (solverType == null) {
            throw new IllegalStateException("Solver type must be provided when creating a new IncrementalSolver");
        } else {
            solver = new IncrementalSolver(solverType);
        }
        return (IncrementalSolver) solver;
    }

    /**
     * Returns the current singleton solver, or {@code null} if none has been created.
     *
     * @return the solver, or {@code null}.
     */
    public static SymbolicSolver getSolver() {
        return solver;
    }

    /**
     * Returns the current singleton as an {@link IncrementalSolver}, or {@code null} if none exists.
     *
     * @return the incremental solver, or {@code null}.
     * @throws IllegalStateException if the existing singleton is not an {@code IncrementalSolver}.
     */
    public static IncrementalSolver getIncrementalSolver() {
        if (solver == null) {
            return null;
        }

        if (solver instanceof IncrementalSolver incrementalSolver) {
            return incrementalSolver;
        }
        throw new IllegalStateException("Solver singleton is not an IncrementalSolver");
    }
}
