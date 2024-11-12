package org.mpisws.symbolic;

import org.mpisws.runtime.JmcRuntime;
import org.sosy_lab.java_smt.api.BooleanFormula;

public class SymbolicFormula {

    public boolean evaluate(SymbolicOperation operation) {
        JmcRuntime.symbolicOperationRequest(Thread.currentThread(), operation);
        return JmcRuntime.solverResult;
    }

    public boolean evaluate(SymbolicBoolean symBool) {
        if (symBool.getEval() != null) {
            return evaluate(symBool.getEval());
        } else {
            SymBoolVariable symVar =
                    JmcRuntime.solver.getSymBoolVariable(symBool.getName());
            BooleanFormula formula = symVar.getVar();
            SymbolicOperation operation = new SymbolicOperation();
            operation.setFormula(formula);
            operation.addBooleanVariable(symBool.getName(), formula);
            return evaluate(operation);
        }
    }
}
