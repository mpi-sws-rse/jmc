package org.mpi_sws.jmc.solver;

import org.mpi_sws.jmc.solver.incremental.IncrementalSolver;

public class SymbolicSolverSingletonFactory {

    private static SymbolicSolver solver;

    public static SymbolicSolver getSolver(SMTSolverTypes solverType) {
        if (solver != null) {
            return solver;
        }

        if (solverType == null) {
            solver = new IncrementalSolver();
        } else {
            solver = new IncrementalSolver(solverType);
        }
        return solver;
    }

    public static IncrementalSolver getIncrementalSolver(SMTSolverTypes solverType) {
        if (solver != null) {
            if (solver instanceof IncrementalSolver incrementalSolver) {
                return incrementalSolver;
            }
            throw new IllegalStateException("Solver singleton is not an IncrementalSolver");
        }

        if (solverType == null) {
            solver = new IncrementalSolver();
        } else {
            solver = new IncrementalSolver(solverType);
        }
        return (IncrementalSolver) solver;
    }
}
