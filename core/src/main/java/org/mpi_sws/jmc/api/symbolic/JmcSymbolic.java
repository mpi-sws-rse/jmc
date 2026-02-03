package org.mpi_sws.jmc.api.symbolic;

import org.mpi_sws.jmc.solver.SymbolicSolver;
import org.mpi_sws.jmc.api.symbolic.bool.SymBoolVariable;
import org.mpi_sws.jmc.api.symbolic.integer.SymIntVariable;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.IntegerFormulaManager;

/**
 * This class provides a static API for integration between the symbolic solver and the symbolic
 * API.
 */
public class JmcSymbolic {
    /**
     * This class is not meant to be instantiated.
     */
    private JmcSymbolic() {
    }

    /**
     * Returns the boolean formula manager of the symbolic solver.
     *
     * @return the boolean formula manager of the symbolic solver.
     */
    public static BooleanFormulaManager getBmgr() {
        SymbolicSolver solver = getSolver();
        return solver.getBmgr();
    }

    /**
     * Returns the integer formula manager of the symbolic solver.
     *
     * @return the integer formula manager of the symbolic solver.
     */
    public static IntegerFormulaManager getImgr() {
        SymbolicSolver solver = getSolver();
        return solver.getImgr();
    }

    /**
     * Returns the symbolic boolean variable corresponding to the given name.
     *
     * @param name the name of the variable
     * @return the symbolic boolean variable
     */
    public static SymBoolVariable getSymBoolVariable(String name) {
        SymbolicSolver solver = getSolver();
        return solver.getSymBoolVariable(name);
    }

    /**
     * Returns the symbolic integer variable corresponding to the given name.
     *
     * @param name the name of the variable
     * @return the symbolic integer variable
     */
    public static SymIntVariable getSymIntVariable(String name) {
        SymbolicSolver solver = getSolver();
        return solver.getSymIntVariable(name);
    }

    /**
     * Returns the symbolic integer variable value corresponding to the given name.
     *
     * @param name the name of the variable
     * @return the symbolic integer variable value
     */
    public static int getSymIntVarValue(String name) {
        SymbolicSolver solver = getSolver();
        return solver.getSymIntVarValue(name);
    }

    /**
     * Returns the symbolic boolean variable value corresponding to the given name.
     *
     * @param name the name of the variable
     * @return the symbolic boolean variable value
     */
    public static boolean getSymBoolVarValue(String name) {
        SymbolicSolver solver = getSolver();
        return solver.getSymBoolVarValue(name);
    }

    /**
     * Returns the symbolic solver used by the JMC runtime.
     *
     * @return the symbolic solver
     */
    private static SymbolicSolver getSolver() {
        // TODO refactor to not depend on TrustStrategy (Use Util)
        return null;
        /*SchedulingStrategy strategy = JmcRuntime.getStrategyInstance();
        if (strategy == null) {
            throw new RuntimeException("Strategy is not initialized");
        }
        if (strategy instanceof TrustStrategy trustStrategy) {
            SymbolicSolver solver = trustStrategy.getSolver();
            if (solver == null) {
                throw new RuntimeException("Solver is not initialized");
            }
            return solver;
        } else {
            throw new RuntimeException("Symbolic API works only with TrustStrategy");
        }*/
    }
}
