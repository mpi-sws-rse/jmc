package org.mpisws.symbolic;

import org.mpisws.runtime.RuntimeEnvironment;
import org.sosy_lab.java_smt.api.BooleanFormula;

public class SymbolicFormula {

    public boolean evaluate(SymbolicOperation operation) {
        RuntimeEnvironment.symbolicOperationRequest(Thread.currentThread(), operation);
        return RuntimeEnvironment.solverResult;
    }

    public boolean evaluate(SymbolicBoolean symBool) {
        if (symBool.getEval() != null) {
            return evaluate(symBool.getEval());
        } else {
            SymBoolVariable symVar =
                    RuntimeEnvironment.solver.getSymBoolVariable(symBool.getName());
            BooleanFormula formula = symVar.getVar();
            SymbolicOperation operation = new SymbolicOperation();
            operation.setFormula(formula);
            operation.addBooleanVariable(symBool.getName(), formula);
            return evaluate(operation);
        }
    }
}
