package org.mpi_sws.jmc.api.symbolic.bool;

import org.mpi_sws.jmc.api.symbolic.InstructionType;
import org.mpi_sws.jmc.api.symbolic.JmcConcreteFormula;
import org.mpi_sws.jmc.api.symbolic.SymbolicOperand;
import org.sosy_lab.java_smt.api.NumeralFormula;
import org.sosy_lab.java_smt.api.BooleanFormula;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JmcBooleanFormula represents a symbolic boolean formula used in symbolic execution.
 * It encapsulates a BooleanFormula along with mappings for integer and boolean variables.
 */
public class JmcBooleanFormula implements SymbolicOperand {

    /**
     * The underlying BooleanFormula representing the symbolic expression.
     */
    private BooleanFormula contextFormula;

    /**
     * The JmcConcreteFormula used for concrete evaluation of the symbolic formula.
     */
    private final JmcConcreteFormula jmcConcreteFormula = new JmcConcreteFormula();

    /**
     * A mapping of integer variable names to their corresponding IntegerFormula representations.
     */
    private Map<String, NumeralFormula.IntegerFormula> integerVariableMap = new HashMap<>();

    /**
     * A mapping of boolean variable names to their corresponding BooleanFormula representations.
     */
    private Map<String, BooleanFormula> booleanVariableMap = new HashMap<>();

    /**
     * Gets the underlying BooleanFormula.
     *
     * @return the underlying BooleanFormula
     */
    public BooleanFormula getFormula() {
        return contextFormula;
    }

    /**
     * Sets the underlying BooleanFormula.
     *
     * @param formula the BooleanFormula to set
     */
    public void setFormula(BooleanFormula formula) {
        contextFormula = formula;
    }

    /**
     * Adds an integer variable to the mapping.
     *
     * @param name    the name of the variable
     * @param formula the IntegerFormula representing the variable
     */
    public void addIntegerVariable(String name, NumeralFormula.IntegerFormula formula) {
        integerVariableMap.put(name, formula);
    }

    /**
     * Gets the integer variable corresponding to the given name.
     *
     * @param name the name of the variable
     * @return the integer variable
     */
    public NumeralFormula.IntegerFormula getIntegerVariable(String name) {
        return integerVariableMap.get(name);
    }

    /**
     * Gets the mapping of integer variables.
     *
     * @return the mapping of integer variables
     */
    public Map<String, NumeralFormula.IntegerFormula> getIntegerVariableMap() {
        return integerVariableMap;
    }

    /**
     * Sets the mapping of integer variables.
     *
     * @param integerVariableMap the mapping to set
     */
    public void setIntegerVariableMap(Map<String, NumeralFormula.IntegerFormula> integerVariableMap) {
        this.integerVariableMap = integerVariableMap;
    }

    /**
     * Adds a boolean variable to the mapping.
     *
     * @param name    the name of the variable
     * @param formula the BooleanFormula representing the variable
     */
    public void addBooleanVariable(String name, BooleanFormula formula) {
        booleanVariableMap.put(name, formula);
    }

    /**
     * Gets the boolean variable corresponding to the given name.
     *
     * @param name the name of the variable
     * @return the boolean variable
     */
    public BooleanFormula getBooleanVariable(String name) {
        return booleanVariableMap.get(name);
    }

    /**
     * Gets the mapping of boolean variables.
     *
     * @return the mapping of boolean variables
     */
    public Map<String, BooleanFormula> getBooleanVariableMap() {
        return booleanVariableMap;
    }

    /**
     * Sets the mapping of boolean variables.
     *
     * @param booleanVariableMap the mapping to set
     */
    public void setBooleanVariableMap(Map<String, BooleanFormula> booleanVariableMap) {
        this.booleanVariableMap = booleanVariableMap;
    }

    /**
     * Checks if this formula is dependent on another formula by checking for shared variables.
     *
     * @param operation the other JmcBooleanFormula to compare with
     * @return true if there is a dependency, false otherwise
     */
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

    /**
     * Sets the JmcConcreteFormula using left and right operands and an operator.
     *
     * @param left     the left operand
     * @param right    the right operand
     * @param operator the operator
     */
    public void setJmcFormula(SymbolicOperand left, SymbolicOperand right, InstructionType operator) {
        jmcConcreteFormula.setLeftOperand(left);
        jmcConcreteFormula.setRightOperand(right);
        jmcConcreteFormula.setOperator(operator);
    }

    /**
     * Sets the JmcConcreteFormula using a list of operands and an operator.
     *
     * @param operands the list of operands
     * @param operator the operator
     */
    public void setJmcFormula(List<SymbolicOperand> operands, InstructionType operator) {
        jmcConcreteFormula.setOperands(operands);
        jmcConcreteFormula.setOperator(operator);
    }

    /**
     * Evaluates the concrete value of the symbolic boolean formula.
     *
     * @return the concrete boolean value
     */
    public boolean concreteEvaluation() {
        boolean result = jmcConcreteFormula.evaluate();
        return result;
    }
}
