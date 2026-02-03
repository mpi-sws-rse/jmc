package org.mpi_sws.jmc.api.symbolic;

import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.api.symbolic.bool.JmcBooleanFormula;
import org.mpi_sws.jmc.api.symbolic.bool.SymBoolVariable;
import org.mpi_sws.jmc.api.symbolic.bool.SymbolicBoolean;

public class SymbolicFormula {

    public boolean evaluate(JmcBooleanFormula operation) {
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.SYMB_OP_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("booleanFormula", operation)
                        .build();
        return JmcRuntime.updateEventAndYield(event);
    }

    public boolean evaluate(SymbolicBoolean symBool) {
        if (symBool.getEval() != null) {
            return evaluate(symBool.getEval());
        } else {
            SymBoolVariable symVar = JmcSymbolic.getSymBoolVariable(symBool.getName());
            org.sosy_lab.java_smt.api.BooleanFormula formula = symVar.getVar();
            JmcBooleanFormula operation = new JmcBooleanFormula();
            operation.setFormula(formula);
            operation.addBooleanVariable(symBool.getName(), formula);
            return evaluate(operation);
        }
    }
}
