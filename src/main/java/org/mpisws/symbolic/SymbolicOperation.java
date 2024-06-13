package org.mpisws.symbolic;

import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula;

import java.util.HashMap;
import java.util.Map;

public class SymbolicOperation {

    private BooleanFormula contextFormula;

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
}
