package org.mpisws.solver;

import java.util.Objects;

public class SymbolicSolverSingletonFactory {

    private static SymbolicSolver solver;

    public static SymbolicSolver getSolver(SolverApproach approach, SMTSolverTypes solverType) {
        if (solver != null) {
            return solver;
        }
        if (Objects.requireNonNull(approach) == SolverApproach.INCREMENTAL) {
            if (solverType == null) {
                solver = new IncrementalSolver();
            } else {
                solver = new IncrementalSolver(solverType);
            }
        } else {
            if (solverType == null) {
                solver = new SimpleSolver();
            } else {
                solver = new SimpleSolver(solverType);
            }
        }
        return solver;
    }
}
