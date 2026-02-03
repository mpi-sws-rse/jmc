package org.mpi_sws.jmc.api.symbolic.integer;

import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.api.symbolic.JmcSymbolic;
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils;

/**
 * The {@link SymbolicInteger} class represents a symbolic integer variable.
 * It extends the {@link AbstractInteger} class and provides methods to read and write
 * the symbolic integer value, as well as to assign expressions or other symbolic integers.
 */
public class SymbolicInteger extends AbstractInteger {
    /**
     * @property {@link #name} is used to store the name of the symbolic integer variable.
     */
    private String name;

    /**
     * @property {@link #eval} is used to store the arithmetic statement assigned to the symbolic integer variable.
     */
    private ArithmeticStatement eval;

    /**
     * @property {@link #value} is used to store the concrete value of the integer.
     */
    private int value;

    /**
     * Default constructor is private to prevent its direct usage.
     */
    private SymbolicInteger() {
        String[] parts = this.toString().split("@");
        this.name = "SymbolicInteger@" + parts[parts.length - 1];
        write();
    }

    /**
     * Private constructor to create a symbolic integer with a specific name and value.
     *
     * @param name  the name of the symbolic integer variable.
     * @param value the concrete value of the integer.
     */
    private SymbolicInteger(String name, int value) {
        this.name = name;
        this.setValue(value);
    }

    /**
     * Public constructor to create a symbolic integer with a specific name.
     *
     * @param name the name of the symbolic integer variable.
     */
    public SymbolicInteger(String name) {
        Long id = JmcRuntime.currentTask();
        this.name = "SymbolicInteger@" + name + "_" + id;
        write();
    }

    /**
     * Assigns an arithmetic statement to the symbolic integer variable.
     *
     * @param expression the arithmetic statement to be assigned.
     */
    public void assign(ArithmeticStatement expression) {
        write(expression);
    }

    /**
     * Assigns another symbolic integer to this symbolic integer variable.
     *
     * @param symbolicInteger the symbolic integer to be assigned.
     */
    public void assign(SymbolicInteger symbolicInteger) {
        write(symbolicInteger);
    }

    /**
     * Makes a deep copy of the symbolic integer.
     *
     * @return a deep copy of the symbolic integer.
     */
    @Override
    public SymbolicInteger clone() {
        SymbolicInteger copy = new SymbolicInteger(name, getValue());
        if (eval != null) {
            copy.setEval(eval.clone());
        }
        return copy;
    }

    /**
     * Gets the name of the symbolic integer variable.
     *
     * @return the name of the symbolic integer variable.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the symbolic integer variable.
     *
     * @param name the name to be set.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the arithmetic statement assigned to the symbolic integer variable.
     *
     * @return the arithmetic statement assigned to the symbolic integer variable.
     */
    public ArithmeticStatement getEval() {
        if (eval != null) {
            return eval;
        } else {
            return null;
        }
    }

    /**
     * Sets the arithmetic statement assigned to the symbolic integer variable.
     *
     * @param eval the arithmetic statement to be set.
     */
    public void setEval(ArithmeticStatement eval) {
        this.eval = eval;
    }

    /**
     * Reads the value of the symbolic integer variable.
     *
     * @return a deep copy of the symbolic integer variable.
     */
    @Override
    public AbstractInteger read() {
        JmcRuntimeUtils.readEventWithoutYield(this,
                "org/mpisws/jmc/symbolic/integer/SymbolicInteger",
                "value",
                "SI");
        AbstractInteger copy = this.clone();

        JmcRuntime.yield();
        return copy;
    }

    /**
     * Writes the value to the symbolic integer variable with another symbolic integer value.
     *
     * @param value the value to be written.
     */
    @Override
    public void write(AbstractInteger value) {
        SymbolicInteger symbolicInteger = (SymbolicInteger) value.read();
        if (symbolicInteger.getEval() != null) {
            this.eval = symbolicInteger.getEval().clone();
        } else {
            this.name = symbolicInteger.getName();
        }

        JmcRuntimeUtils.writeEventWithoutYield(this,
                symbolicInteger,
                "org/mpisws/jmc/symbolic/integer/SymbolicInteger",
                "value",
                "SI");
        JmcRuntime.yield();
    }

    /**
     * Writes the value to the symbolic integer variable with an arithmetic statement value.
     *
     * @param value the value to be written.
     */
    @Override
    public void write(ArithmeticStatement value) {
        this.eval = value.clone();

        JmcRuntimeUtils.writeEventWithoutYield(this,
                value,
                "org/mpisws/jmc/symbolic/integer/SymbolicInteger",
                "value",
                "SI");
        JmcRuntime.yield();
    }

    /**
     * Writes the initial value to the symbolic integer variable.
     */
    private void write() {
        JmcRuntimeUtils.writeEventWithoutYield(this,
                value,
                "org/mpisws/jmc/symbolic/integer/SymbolicInteger",
                "value",
                "SI");
        JmcRuntime.yield();
    }

    /**
     * Evaluates and returns the integer value of the symbolic integer variable.
     *
     * @return the integer value of the symbolic integer variable.
     */
    public int getIntValue() {
        if (this.getEval() != null) {
            int leftValue;
            if (this.getEval().getLeft() instanceof SymbolicInteger left) {
                leftValue = left.getIntValue();
            } else {
                leftValue = this.getEval().getLeft().getValue();
            }
            int rightValue;
            if (this.getEval().getRight() instanceof SymbolicInteger right) {
                rightValue = right.getIntValue();
            } else {
                rightValue = this.getEval().getRight().getValue();
            }
            switch (this.getEval().getOperator()) {
                case ADD:
                    return leftValue + rightValue;
                case SUB:
                    return leftValue - rightValue;
                case MUL:
                    return leftValue * rightValue;
                case DIV:
                    if (rightValue == 0) {
                        throw new ArithmeticException("[JMC Formula Message] Division by zero");
                    }
                    return leftValue / rightValue;
                case MOD:
                    if (rightValue == 0) {
                        throw new ArithmeticException("[JMC Formula Message] Modulo by zero");
                    }
                    return leftValue % rightValue;
                default:
                    throw new IllegalArgumentException(
                            "[JMC Formula Message] Unsupported operator");
            }
        } else {
            return JmcSymbolic.getSymIntVarValue(this.getName());
        }
    }
}
