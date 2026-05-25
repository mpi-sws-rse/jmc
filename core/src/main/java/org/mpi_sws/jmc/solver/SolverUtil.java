package org.mpi_sws.jmc.solver;

import org.mpi_sws.jmc.api.symbolic.bool.SymBoolVariable;
import org.mpi_sws.jmc.api.symbolic.integer.SymIntVariable;
import org.mpi_sws.jmc.solver.incremental.IncrementalSolver;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.IntegerFormulaManager;

public class SolverUtil {
    private SolverUtil() {
    }

    public static SymbolicSolver createSymbolicSolver(SMTSolverTypes solverType) {
        return SymbolicSolverSingletonFactory.getSolver(solverType);
    }

    public static IncrementalSolver createIncrementalSolver(SMTSolverTypes solverType) {
        return SymbolicSolverSingletonFactory.getIncrementalSolver(solverType);
    }

    public static SymbolicSolver getSolver() {
        return SymbolicSolverSingletonFactory.getSolver();
    }

    public static IncrementalSolver getIncrementalSolver() {
        return SymbolicSolverSingletonFactory.getIncrementalSolver();
    }

    public static BooleanFormulaManager getBmgr() {
        SymbolicSolver solver = getSolver();
        if (solver == null) {
            throw new IllegalStateException("SymbolicSolver has not been initialized. Please create a SymbolicSolver" +
                    " before accessing the BooleanFormulaManager.");
        }
        return solver.getBmgr();
    }

    public static IntegerFormulaManager getImgr() {
        SymbolicSolver solver = getSolver();
        if (solver == null) {
            throw new IllegalStateException("SymbolicSolver has not been initialized. Please create a SymbolicSolver" +
                    " before accessing the IntegerFormulaManager.");
        }
        return solver.getImgr();
    }

    public static SymBoolVariable getSymBoolVariable(String name) {
        SymbolicSolver solver = getSolver();
        if (solver == null) {
            throw new IllegalStateException("SymbolicSolver has not been initialized. Please create a SymbolicSolver" +
                    " before accessing symbolic variables.");
        }
        return solver.getSymBoolVariable(name);
    }

    public static SymIntVariable getSymIntVariable(String name) {
        SymbolicSolver solver = getSolver();
        if (solver == null) {
            throw new IllegalStateException("SymbolicSolver has not been initialized. Please create a SymbolicSolver" +
                    " before accessing symbolic variables.");
        }
        return solver.getSymIntVariable(name);
    }

    public static int getSymIntVarValue(String name) {
        SymbolicSolver solver = getSolver();
        if (solver == null) {
            throw new IllegalStateException("SymbolicSolver has not been initialized. Please create a SymbolicSolver" +
                    " before accessing symbolic variable values.");
        }
        return solver.getSymIntVarValue(name);
    }

    public static boolean getSymBoolVarValue(String name) {
        SymbolicSolver solver = getSolver();
        if (solver == null) {
            throw new IllegalStateException("SymbolicSolver has not been initialized. Please create a SymbolicSolver" +
                    " before accessing symbolic variable values.");
        }
        return solver.getSymBoolVarValue(name);
    }
}
