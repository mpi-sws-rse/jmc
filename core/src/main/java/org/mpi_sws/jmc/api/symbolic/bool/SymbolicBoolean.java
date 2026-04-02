package org.mpi_sws.jmc.api.symbolic.bool;

import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils;

/**
 * The {@link SymbolicBoolean} class represents a symbolic boolean variable.
 * It extends the {@link AbstractBoolean} class and provides methods to read and write
 * the symbolic boolean value, as well as to assign expressions or other symbolic booleans.
 */
public class SymbolicBoolean extends AbstractBoolean {

    /**
     * The name of the symbolic boolean variable.
     */
    private String name;

    /**
     * The symbolic boolean expression associated with this variable.
     */
    private JmcBooleanFormula eval;

    /**
     * The concrete boolean value of the symbolic boolean variable.
     */
    private boolean value;

    /**
     * Default constructor that initializes the symbolic boolean variable with a unique name.
     */
    private SymbolicBoolean() {
        String[] parts = this.toString().split("@");
        this.name = "SymbolicBoolean@" + parts[parts.length - 1];
        write();
    }

    /**
     * Constructor that initializes the symbolic boolean variable with a given name and value.
     *
     * @param name  the name of the symbolic boolean variable
     * @param value the concrete boolean value
     */
    private SymbolicBoolean(String name, boolean value) {
        this.name = name;
        this.setValue(value);
    }

    /**
     * Public constructor that initializes the symbolic boolean variable with a given name.
     *
     * @param name the name of the symbolic boolean variable
     */
    public SymbolicBoolean(String name) {
        // TODO: The following line must be refactored
        Long id = JmcRuntime.currentTask();
        this.name = "SymbolicBoolean@" + name + "_" + id;
        this.write();
    }

    /**
     * Assigns a symbolic boolean expression to this variable.
     *
     * @param expression the symbolic boolean expression to assign
     */
    public void assign(JmcBooleanFormula expression) {
        write(expression);
    }

    /**
     * Assigns another symbolic boolean variable to this variable.
     *
     * @param symbolicBoolean the symbolic boolean variable to assign
     */
    public void assign(SymbolicBoolean symbolicBoolean) {
        write(symbolicBoolean);
    }

    /**
     * Makes a deep copy of this SymbolicBoolean.
     *
     * @return a deep copy of this SymbolicBoolean
     */
    @Override
    public SymbolicBoolean clone() {
        SymbolicBoolean copy = new SymbolicBoolean(name, getValue());
        if (eval != null) {
            JmcBooleanFormula expressionCopy = new JmcBooleanFormula();
            expressionCopy.setFormula(eval.getFormula());
            expressionCopy.setIntegerVariableMap(eval.getIntegerVariableMap());
            copy.setEval(expressionCopy);
        }
        return copy;
    }

    /**
     * Gets the name of the symbolic boolean variable.
     *
     * @return the name of the symbolic boolean variable
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the symbolic boolean variable.
     *
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the symbolic boolean expression associated with this variable.
     *
     * @return the symbolic boolean expression
     */
    public JmcBooleanFormula getEval() {
        return eval;
    }

    /**
     * Sets the symbolic boolean expression associated with this variable.
     *
     * @param eval the symbolic boolean expression to set
     */
    public void setEval(JmcBooleanFormula eval) {
        this.eval = eval;
    }

    /**
     * Reads the value of this SymbolicBoolean.
     *
     * @return the symbolic value
     */
    @Override
    public AbstractBoolean read() {
        JmcRuntimeUtils.readEventWithoutYield(this, "org/mpisws/jmc/symbolic/bool/SymbolicBoolean", "value", "SZ");
        AbstractBoolean copy = this.clone();
        JmcRuntime.yield();
        return copy;
    }

    /**
     * Writes the value of this SymbolicBoolean with another SymbolicBoolean value.
     *
     * @param value the value to be written.
     */
    @Override
    public void write(SymbolicBoolean value) {
        SymbolicBoolean symbolicBoolean = (SymbolicBoolean) value.read();

        if (symbolicBoolean.getEval() != null) {
            JmcBooleanFormula expressionCopy = new JmcBooleanFormula();
            expressionCopy.setFormula(symbolicBoolean.eval.getFormula());
            expressionCopy.setIntegerVariableMap(symbolicBoolean.eval.getIntegerVariableMap());
            this.eval = expressionCopy;
        } else {
            this.name = symbolicBoolean.getName();
        }

        JmcRuntimeUtils.writeEventWithoutYield(this,
                symbolicBoolean,
                "org/mpisws/jmc/symbolic/bool/SymbolicBoolean",
                "value",
                "SZ");
        JmcRuntime.yield();
    }

    /**
     * Writes the value of this SymbolicBoolean with a JmcBooleanFormula value.
     *
     * @param value the value to be written.
     */
    @Override
    public void write(JmcBooleanFormula value) {
        JmcBooleanFormula expressionCopy = new JmcBooleanFormula();
        expressionCopy.setFormula(value.getFormula());
        expressionCopy.setIntegerVariableMap(value.getIntegerVariableMap());
        this.eval = expressionCopy;

        JmcRuntimeUtils.writeEventWithoutYield(this,
                value,
                "org/mpisws/jmc/symbolic/bool/SymbolicBoolean",
                "value",
                "SZ");
        JmcRuntime.yield();
    }

    /**
     * Writes the concrete boolean value of this SymbolicBoolean.
     */
    private void write() {
        JmcRuntimeUtils.writeEventWithoutYield(this,
                value,
                "org/mpisws/jmc/symbolic/bool/SymbolicBoolean",
                "value",
                "SZ");
        JmcRuntime.yield();
    }
}
