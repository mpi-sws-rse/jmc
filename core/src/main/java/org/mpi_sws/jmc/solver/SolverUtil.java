package org.mpi_sws.jmc.solver;

import org.mpi_sws.jmc.api.symbolic.bool.SymBoolVariable;
import org.mpi_sws.jmc.api.symbolic.integer.SymIntVariable;
import org.mpi_sws.jmc.solver.incremental.IncrementalSolver;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.IntegerFormulaManager;

public class SolverUtil {
    private SolverUtil() {
    }

    public static SymbolicSolver getSolver() {
        return SymbolicSolverSingletonFactory.getSolver(null);
    }

    public static SymbolicSolver getSolver(SMTSolverTypes solverType) {
        return SymbolicSolverSingletonFactory.getSolver(solverType);
    }

    public static IncrementalSolver getIncrementalSolver() {
        return SymbolicSolverSingletonFactory.getIncrementalSolver(null);
    }

    public static IncrementalSolver getIncrementalSolver(SMTSolverTypes solverType) {
        return SymbolicSolverSingletonFactory.getIncrementalSolver(solverType);
    }

    public static BooleanFormulaManager getBmgr() {
        SymbolicSolver solver = getSolver();
        return solver.getBmgr();
    }

    public static IntegerFormulaManager getImgr() {
        SymbolicSolver solver = getSolver();
        return solver.getImgr();
    }

    public static SymBoolVariable getSymBoolVariable(String name) {
        SymbolicSolver solver = getSolver();
        return solver.getSymBoolVariable(name);
    }

    public static SymIntVariable getSymIntVariable(String name) {
        SymbolicSolver solver = getSolver();
        return solver.getSymIntVariable(name);
    }

    public static int getSymIntVarValue(String name) {
        SymbolicSolver solver = getSolver();
        return solver.getSymIntVarValue(name);
    }

    public static boolean getSymBoolVarValue(String name) {
        SymbolicSolver solver = getSolver();
        return solver.getSymBoolVarValue(name);
    }
}
