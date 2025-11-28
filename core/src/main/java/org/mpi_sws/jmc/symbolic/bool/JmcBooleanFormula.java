package org.mpi_sws.jmc.symbolic.bool;

import org.mpi_sws.jmc.symbolic.InstructionType;
import org.mpi_sws.jmc.symbolic.JmcFormula;
import org.mpi_sws.jmc.symbolic.SymbolicOperand;
import org.sosy_lab.java_smt.api.NumeralFormula;
import org.sosy_lab.java_smt.api.BooleanFormula;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JmcBooleanFormula implements SymbolicOperand {

    private BooleanFormula contextFormula;

    private final JmcFormula jmcFormula = new JmcFormula();

    private Map<String, NumeralFormula.IntegerFormula> integerVariableMap = new HashMap<>();

    private Map<String, BooleanFormula> booleanVariableMap = new HashMap<>();

    public BooleanFormula getFormula() {
        return contextFormula;
    }

    public void setFormula(BooleanFormula formula) {
        contextFormula = formula;
    }

    public void addIntegerVariable(String name, NumeralFormula.IntegerFormula formula) {
        integerVariableMap.put(name, formula);
    }

    public NumeralFormula.IntegerFormula getIntegerVariable(String name) {
        return integerVariableMap.get(name);
    }

    public Map<String, NumeralFormula.IntegerFormula> getIntegerVariableMap() {
        return integerVariableMap;
    }

    public void setIntegerVariableMap(Map<String, NumeralFormula.IntegerFormula> integerVariableMap) {
        this.integerVariableMap = integerVariableMap;
    }

    public void addBooleanVariable(String name, BooleanFormula formula) {
        booleanVariableMap.put(name, formula);
    }

    public BooleanFormula getBooleanVariable(String name) {
        return booleanVariableMap.get(name);
    }

    public Map<String, BooleanFormula> getBooleanVariableMap() {
        return booleanVariableMap;
    }

    public void setBooleanVariableMap(Map<String, BooleanFormula> booleanVariableMap) {
        this.booleanVariableMap = booleanVariableMap;
    }

    public boolean isFormulaDependent(JmcBooleanFormula operation) {
        for (Map.Entry<String, NumeralFormula.IntegerFormula> entry : this.getIntegerVariableMap().entrySet()) {
            String key = entry.getKey();
            NumeralFormula.IntegerFormula value = entry.getValue();
            NumeralFormula.IntegerFormula valueInFormula2 = operation.getIntegerVariableMap().get(key);
            if (valueInFormula2 != null && valueInFormula2.equals(value)) {
                return true;
            }
        }
        for (Map.Entry<String, BooleanFormula> entry : this.getBooleanVariableMap().entrySet()) {
            String key = entry.getKey();
            BooleanFormula value = entry.getValue();
            BooleanFormula valueInFormula2 = operation.getBooleanVariableMap().get(key);
            if (valueInFormula2 != null && valueInFormula2.equals(value)) {
                return true;
            }
        }
        return false;
    }

    public void setJmcFormula(SymbolicOperand left, SymbolicOperand right, InstructionType operator) {
        jmcFormula.setLeftOperand(left);
        jmcFormula.setRightOperand(right);
        jmcFormula.setOperator(operator);

    }

    public void setJmcFormula(List<SymbolicOperand> operands, InstructionType operator) {
        jmcFormula.setOperands(operands);
        jmcFormula.setOperator(operator);
    }

    public boolean concreteEvaluation() {
        boolean result = jmcFormula.evaluate();
        return result;
    }
}
