package org.mpisws.symbolic;

import org.mpisws.runtime.RuntimeEnvironment;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SymbolicOperation implements SymbolicOperand {

    private BooleanFormula contextFormula;

    private final JMCFormula jmcFormula = new JMCFormula();

    private Map<String, IntegerFormula> integerVariableMap = new HashMap<>();

    private Map<String, BooleanFormula> booleanVariableMap = new HashMap<>();

    public BooleanFormula getFormula() {
        return contextFormula;
    }

    public void setFormula(BooleanFormula formula) {
        contextFormula = formula;
    }

    public void addIntegerVariable(String name, IntegerFormula formula) {
        integerVariableMap.put(name, formula);
    }

    public IntegerFormula getIntegerVariable(String name) {
        return integerVariableMap.get(name);
    }

    public Map<String, IntegerFormula> getIntegerVariableMap() {
        return integerVariableMap;
    }

    public void setIntegerVariableMap(Map<String, IntegerFormula> integerVariableMap) {
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

    public boolean isFormulaDependent(SymbolicOperation operation) {
        // For debugging purposes
        System.out.println("[Dbugging] : The first formula is: " + this.getFormula());
        System.out.println("[Debugging] : The second formula is: " + operation.getFormula());
        System.out.println("[Debugging] : Checking if the formula is dependent on the other formula");
        System.out.println("[Debugging] : The symbolic variabels of the first formula are: ");
        for (Map.Entry<String, IntegerFormula> entry : this.getIntegerVariableMap().entrySet()) {
            System.out.println("[Debugging] : " + entry.getKey() + " : " + entry.getValue());
        }
        for (Map.Entry<String, BooleanFormula> entry : this.getBooleanVariableMap().entrySet()) {
            System.out.println("[Debugging] : " + entry.getKey() + " : " + entry.getValue());
        }
        System.out.println("[Debugging] : The symbolic variabels of the second formula are: ");
        for (Map.Entry<String, IntegerFormula> entry : operation.getIntegerVariableMap().entrySet()) {
            System.out.println("[Debugging] : " + entry.getKey() + " : " + entry.getValue());
        }
        for (Map.Entry<String, BooleanFormula> entry : operation.getBooleanVariableMap().entrySet()) {
            System.out.println("[Debugging] : " + entry.getKey() + " : " + entry.getValue());
        }


        for (Map.Entry<String, IntegerFormula> entry : this.getIntegerVariableMap().entrySet()) {
            String key = entry.getKey();
            IntegerFormula value = entry.getValue();
            IntegerFormula valueInFormula2 = operation.getIntegerVariableMap().get(key);
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
        //System.out.println("[Debugging] : The JMC formula is: " + getFormula());
        //System.out.println("[Debugging] : The result of the symbolic operation is: " + result);
        //System.out.println("[Debugging] : The value of the symbolic variables are: ");
//
        return result;
    }

}
