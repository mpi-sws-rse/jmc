package org.mpi_sws.jmc.api.symbolic;

import org.mpi_sws.jmc.api.symbolic.bool.JmcBooleanFormula;
import org.mpi_sws.jmc.api.symbolic.bool.SymBoolVariable;
import org.mpi_sws.jmc.api.symbolic.bool.SymbolicBoolean;
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils;
import org.mpi_sws.jmc.solver.SolverUtil;

/**
 * The user-facing entry point for evaluating a symbolic constraint — the ConDpor {@code evaluate()}
 * operation.
 *
 * <p>Evaluating a symbolic boolean is the point at which the concolic exploration <em>branches</em>
 * on data. Each {@code evaluate} call reports a symbolic event to the runtime (via {@link
 * JmcRuntimeUtils#SymEvent}); the {@code trust} strategy's ConDpor machinery then decides the branch
 * taken now and whether the other branch must also be explored, and hands the boolean result back to
 * the program so it can be used in ordinary control flow (e.g. an {@code if}).
 */
public class SymbolicFormula {

    /**
     * Evaluates a symbolic boolean formula, registering it as a ConDpor branch point.
     *
     * @param operation the boolean formula to evaluate.
     * @return the concrete boolean outcome chosen by the strategy.
     */
    public boolean evaluate(JmcBooleanFormula operation) {
        return JmcRuntimeUtils.SymEvent(operation);
    }

    /**
     * Evaluates a symbolic boolean variable, using its assigned expression if it has one and
     * otherwise the underlying solver variable.
     *
     * @param symBool the symbolic boolean to evaluate.
     * @return the concrete boolean outcome chosen by the strategy.
     */
    public boolean evaluate(SymbolicBoolean symBool) {
        if (symBool.getEval() != null) {
            return evaluate(symBool.getEval());
        } else {
            SymBoolVariable symVar = SolverUtil.getSymBoolVariable(symBool.getName());
            org.sosy_lab.java_smt.api.BooleanFormula formula = symVar.getVar();
            JmcBooleanFormula operation = new JmcBooleanFormula();
            operation.setFormula(formula);
            operation.addBooleanVariable(symBool.getName(), formula);
            return evaluate(operation);
        }
    }
}
