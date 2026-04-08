package org.mpi_sws.jmc.api.symbolic;

import org.mpi_sws.jmc.api.symbolic.bool.JmcBooleanFormula;
import org.mpi_sws.jmc.api.symbolic.bool.SymBoolVariable;
import org.mpi_sws.jmc.api.symbolic.bool.SymbolicBoolean;
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils;
import org.mpi_sws.jmc.solver.SolverUtil;

public class SymbolicFormula {

    public boolean evaluate(JmcBooleanFormula operation) {
        return JmcRuntimeUtils.SymEvent(operation);
    }

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
