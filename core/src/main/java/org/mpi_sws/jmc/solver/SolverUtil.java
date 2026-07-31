package org.mpi_sws.jmc.solver;

import org.mpi_sws.jmc.api.symbolic.bool.SymBoolVariable;
import org.mpi_sws.jmc.api.symbolic.integer.SymIntVariable;
import org.mpi_sws.jmc.solver.incremental.IncrementalSolver;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.IntegerFormulaManager;

/**
 * Static access to the process-wide symbolic solver and its JavaSMT managers/variables.
 *
 * <p>The solver is a singleton (see {@link SymbolicSolverSingletonFactory}); the symbolic API and
 * the formula builders reach it through these helpers rather than threading a solver reference
 * everywhere. All accessors fail fast if the solver has not been created yet.
 */
public class SolverUtil {
    private SolverUtil() {
    }

    /**
     * Creates (or returns the existing) singleton symbolic solver of the given type.
     *
     * @param solverType the SMT backend to use.
     * @return the symbolic solver.
     */
    public static SymbolicSolver createSymbolicSolver(SMTSolverTypes solverType) {
        return SymbolicSolverSingletonFactory.getSolver(solverType);
    }

    /**
     * Creates (or returns the existing) singleton incremental solver of the given type.
     *
     * @param solverType the SMT backend to use.
     * @return the incremental solver.
     */
    public static IncrementalSolver createIncrementalSolver(SMTSolverTypes solverType) {
        return SymbolicSolverSingletonFactory.getIncrementalSolver(solverType);
    }

    /**
     * Returns the current symbolic solver, or {@code null} if none has been created.
     *
     * @return the symbolic solver, or {@code null}.
     */
    public static SymbolicSolver getSolver() {
        return SymbolicSolverSingletonFactory.getSolver();
    }

    /**
     * Returns the current incremental solver, or {@code null} if none has been created.
     *
     * @return the incremental solver, or {@code null}.
     */
    public static IncrementalSolver getIncrementalSolver() {
        return SymbolicSolverSingletonFactory.getIncrementalSolver();
    }

    /**
     * Returns the JavaSMT boolean formula manager of the current solver.
     *
     * @return the boolean formula manager.
     * @throws IllegalStateException if no solver has been created.
     */
    public static BooleanFormulaManager getBmgr() {
        SymbolicSolver solver = getSolver();
        if (solver == null) {
            throw new IllegalStateException("SymbolicSolver has not been initialized. Please create a SymbolicSolver" +
                    " before accessing the BooleanFormulaManager.");
        }
        return solver.getBmgr();
    }

    /**
     * Returns the JavaSMT integer formula manager of the current solver.
     *
     * @return the integer formula manager.
     * @throws IllegalStateException if no solver has been created.
     */
    public static IntegerFormulaManager getImgr() {
        SymbolicSolver solver = getSolver();
        if (solver == null) {
            throw new IllegalStateException("SymbolicSolver has not been initialized. Please create a SymbolicSolver" +
                    " before accessing the IntegerFormulaManager.");
        }
        return solver.getImgr();
    }

    /**
     * Returns (creating if needed) the symbolic boolean variable with the given name.
     *
     * @param name the variable name.
     * @return the symbolic boolean variable.
     * @throws IllegalStateException if no solver has been created.
     */
    public static SymBoolVariable getSymBoolVariable(String name) {
        SymbolicSolver solver = getSolver();
        if (solver == null) {
            throw new IllegalStateException("SymbolicSolver has not been initialized. Please create a SymbolicSolver" +
                    " before accessing symbolic variables.");
        }
        return solver.getSymBoolVariable(name);
    }

    /**
     * Returns (creating if needed) the symbolic integer variable with the given name.
     *
     * @param name the variable name.
     * @return the symbolic integer variable.
     * @throws IllegalStateException if no solver has been created.
     */
    public static SymIntVariable getSymIntVariable(String name) {
        SymbolicSolver solver = getSolver();
        if (solver == null) {
            throw new IllegalStateException("SymbolicSolver has not been initialized. Please create a SymbolicSolver" +
                    " before accessing symbolic variables.");
        }
        return solver.getSymIntVariable(name);
    }

    /**
     * Returns the current concrete value of the named symbolic integer variable.
     *
     * @param name the variable name.
     * @return the concrete integer value.
     * @throws IllegalStateException if no solver has been created.
     */
    public static int getSymIntVarValue(String name) {
        SymbolicSolver solver = getSolver();
        if (solver == null) {
            throw new IllegalStateException("SymbolicSolver has not been initialized. Please create a SymbolicSolver" +
                    " before accessing symbolic variable values.");
        }
        return solver.getSymIntVarValue(name);
    }

    /**
     * Returns the current concrete value of the named symbolic boolean variable.
     *
     * @param name the variable name.
     * @return the concrete boolean value.
     * @throws IllegalStateException if no solver has been created.
     */
    public static boolean getSymBoolVarValue(String name) {
        SymbolicSolver solver = getSolver();
        if (solver == null) {
            throw new IllegalStateException("SymbolicSolver has not been initialized. Please create a SymbolicSolver" +
                    " before accessing symbolic variable values.");
        }
        return solver.getSymBoolVarValue(name);
    }
}
