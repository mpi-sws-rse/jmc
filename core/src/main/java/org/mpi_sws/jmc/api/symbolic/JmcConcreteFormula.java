package org.mpi_sws.jmc.api.symbolic;

import org.mpi_sws.jmc.api.symbolic.bool.AbstractBoolean;
import org.mpi_sws.jmc.api.symbolic.bool.JmcBooleanFormula;
import org.mpi_sws.jmc.api.symbolic.bool.ConcreteBoolean;
import org.mpi_sws.jmc.api.symbolic.bool.SymbolicBoolean;
import org.mpi_sws.jmc.api.symbolic.integer.AbstractInteger;
import org.mpi_sws.jmc.api.symbolic.integer.ConcreteInteger;
import org.mpi_sws.jmc.api.symbolic.integer.SymbolicInteger;

import java.util.HashSet;
import java.util.List;

public class JmcConcreteFormula {

    /**
     * The left operand of the formula.
     */
    private SymbolicOperand leftOperand;

    /**
     * The right operand of the formula.
     */
    private SymbolicOperand rightOperand;

    /**
     * The list of operands for operators that take multiple operands.
     */
    private List<SymbolicOperand> operands;

    /**
     * The operator of the formula.
     */
    private InstructionType operator;

    /**
     * Sets the left operand of the formula.
     *
     * @param leftOperand the left operand to set
     */
    public void setLeftOperand(SymbolicOperand leftOperand) {
        this.leftOperand = leftOperand;
    }

    /**
     * Sets the right operand of the formula.
     *
     * @param rightOperand the right operand to set
     */
    public void setRightOperand(SymbolicOperand rightOperand) {
        this.rightOperand = rightOperand;
    }

    /**
     * Sets the operator of the formula.
     *
     * @param operator the operator to set
     */
    public void setOperator(InstructionType operator) {
        this.operator = operator;
    }

    /**
     * Sets the list of operands
     *
     * @param operands the list of operands to set
     */
    public void setOperands(List<SymbolicOperand> operands) {
        this.operands = operands;
    }

    /**
     * Evaluates the formula based on the operator and operands.
     *
     * @return the result of the evaluation
     */
    public boolean evaluate() {
        if (operator == null) {
            throw new RuntimeException("Operator is not set");
        } else if (operator == InstructionType.EQ) {
            return evalEqual();
        } else if (operator == InstructionType.NEQ) {
            return evalNeq();
        } else if (operator == InstructionType.GT) {
            return evalGreater();
        } else if (operator == InstructionType.LT) {
            return evalLess();
        } else if (operator == InstructionType.GEQ) {
            return evalGreaterEqual();
        } else if (operator == InstructionType.LEQ) {
            return evalLessEqual();
        } else if (operator == InstructionType.AND) {
            return evalAnd();
        } else if (operator == InstructionType.OR) {
            return evalOr();
        } else if (operator == InstructionType.IMPLIES) {
            return evalImplies();
        } else if (operator == InstructionType.IFF) {
            return evalIff();
        } else if (operator == InstructionType.XOR) {
            return evalXor();
        } else if (operator == InstructionType.NOT) {
            return evalNot();
        } else if (operator == InstructionType.ATOM) {
            return evalAtom();
        } else if (operator == InstructionType.DISTINCT) {
            return evalDistinct();
        } else {
            throw new RuntimeException("Unsupported operator");
        }
    }

    /**
     * Evaluates the DISTINCT operator.
     *
     * @return true if all operands are distinct, false otherwise
     */
    private boolean evalDistinct() {
        if (operands == null || operands.size() == 0) {
            throw new RuntimeException("Distinct operator must have at least two operands");
        }

        if (operands.size() == 1) {
            return true; // Single element is always distinct
        }

        HashSet<Integer> seenValues = new HashSet<>();
        for (SymbolicOperand operand : operands) {
            if (operand instanceof AbstractInteger intOp) {
                int value = getIntValue(intOp);
                if (!seenValues.add(value)) {
                    seenValues.clear();
                    seenValues = null;
                    return false; // Duplicate found
                }
            } else {
                throw new RuntimeException("Invalid operand for operator DISTINCT");
            }
        }
        seenValues.clear();
        seenValues = null;
        return true; // All elements are distinct
    }

    /**
     * Evaluates the ATOM operator.
     *
     * @return the boolean value of the left operand
     */
    private boolean evalAtom() {
        if (rightOperand != null) {
            throw new IllegalArgumentException("Right operand must be null for ATOM operator");
        } else if (leftOperand instanceof AbstractBoolean left) {
            return getBoolValue(left);
        } else if (leftOperand instanceof JmcBooleanFormula left) {
            return left.concreteEvaluation();
        } else {
            throw new RuntimeException("Invalid operand for operator ATOM");
        }
    }

    /**
     * Evaluates the NOT operator.
     *
     * @return the negation of the boolean value of the left operand
     */
    private boolean evalNot() {
        if (rightOperand != null) {
            throw new IllegalArgumentException("Right operand must be null for NOT operator");
        } else if (leftOperand instanceof AbstractBoolean left) {
            return !getBoolValue(left);
        } else if (leftOperand instanceof JmcBooleanFormula left) {
            return !left.concreteEvaluation();
        } else {
            throw new RuntimeException("Invalid operand for operator NOT");
        }
    }

    /**
     * Evaluates the EQ (equal) operator.
     *
     * @return true if the left and right operands are equal, false otherwise
     */
    private boolean evalEqual() {
        if (leftOperand instanceof AbstractInteger left
                && rightOperand instanceof AbstractInteger right) {
            return getIntValue(left) == getIntValue(right);
        } else {
            throw new RuntimeException("Invalid operands for operator EQ");
        }
    }

    /**
     * Evaluates the NEQ (not equal) operator.
     *
     * @return true if the left and right operands are not equal, false otherwise
     */
    private boolean evalNeq() {
        if (leftOperand instanceof AbstractInteger left
                && rightOperand instanceof AbstractInteger right) {
            return getIntValue(left) != getIntValue(right);
        } else {
            throw new RuntimeException("Invalid operands for operator NEQ");
        }
    }

    /**
     * Evaluates the GT (greater than) operator.
     *
     * @return true if the left operand is greater than the right operand, false otherwise
     */
    private boolean evalGreater() {
        if (leftOperand instanceof AbstractInteger left
                && rightOperand instanceof AbstractInteger right) {
            return getIntValue(left) > getIntValue(right);
        } else {
            throw new RuntimeException("Invalid operands for operator GT");
        }
    }

    /**
     * Evaluates the LT (less than) operator.
     *
     * @return true if the left operand is less than the right operand, false otherwise
     */
    private boolean evalLess() {
        if (leftOperand instanceof AbstractInteger left
                && rightOperand instanceof AbstractInteger right) {
            return getIntValue(left) < getIntValue(right);
        } else {
            throw new RuntimeException("Invalid operands for operator LT");
        }
    }

    /**
     * Evaluates the LEQ (less than or equal to) operator.
     *
     * @return true if the left operand is less than or equal to the right operand, false otherwise
     */
    private boolean evalLessEqual() {
        if (leftOperand instanceof AbstractInteger left
                && rightOperand instanceof AbstractInteger right) {
            return getIntValue(left) <= getIntValue(right);
        } else {
            throw new RuntimeException("Invalid operands for operator LEQ");
        }
    }

    /**
     * Evaluates the GEQ (greater than or equal to) operator.
     *
     * @return true if the left operand is greater than or equal to the right operand, false otherwise
     */
    private boolean evalGreaterEqual() {
        if (leftOperand instanceof AbstractInteger left
                && rightOperand instanceof AbstractInteger right) {
            return getIntValue(left) >= getIntValue(right);
        } else {
            throw new RuntimeException("Invalid operands for operator GEQ");
        }
    }

    /**
     * Evaluates the AND operator.
     *
     * @return true if both operands are true, false otherwise
     */
    private boolean evalAnd() {
        if (leftOperand instanceof AbstractBoolean left
                && rightOperand instanceof AbstractBoolean right) {
            return getBoolValue(left) && getBoolValue(right);
        } else if (leftOperand instanceof JmcBooleanFormula left
                && rightOperand instanceof AbstractBoolean right) {
            return left.concreteEvaluation() && getBoolValue(right);
        } else if (leftOperand instanceof AbstractBoolean left
                && rightOperand instanceof JmcBooleanFormula right) {
            return getBoolValue(left) && right.concreteEvaluation();
        } else if (leftOperand instanceof JmcBooleanFormula left
                && rightOperand instanceof JmcBooleanFormula right) {
            return left.concreteEvaluation() && right.concreteEvaluation();
        } else {
            throw new RuntimeException("Invalid operands for operator AND");
        }
    }

    /**
     * Evaluates the OR operator.
     *
     * @return true if at least one operand is true, false otherwise
     */
    private boolean evalOr() {
        if (leftOperand instanceof AbstractBoolean left
                && rightOperand instanceof AbstractBoolean right) {
            return getBoolValue(left) || getBoolValue(right);
        } else if (leftOperand instanceof JmcBooleanFormula left
                && rightOperand instanceof AbstractBoolean right) {
            return left.concreteEvaluation() || getBoolValue(right);
        } else if (leftOperand instanceof AbstractBoolean left
                && rightOperand instanceof JmcBooleanFormula right) {
            return getBoolValue(left) || right.concreteEvaluation();
        } else if (leftOperand instanceof JmcBooleanFormula left
                && rightOperand instanceof JmcBooleanFormula right) {
            return left.concreteEvaluation() || right.concreteEvaluation();
        } else {
            throw new RuntimeException("Invalid operands for operator OR");
        }
    }

    /**
     * Evaluates the IMPLIES operator.
     *
     * @return true if the implication holds, false otherwise
     */
    private boolean evalImplies() {
        if (leftOperand instanceof AbstractBoolean left
                && rightOperand instanceof AbstractBoolean right) {
            return !getBoolValue(left) || getBoolValue(right);
        } else if (leftOperand instanceof JmcBooleanFormula left
                && rightOperand instanceof AbstractBoolean right) {
            return !left.concreteEvaluation() || getBoolValue(right);
        } else if (leftOperand instanceof AbstractBoolean left
                && rightOperand instanceof JmcBooleanFormula right) {
            return !getBoolValue(left) || right.concreteEvaluation();
        } else if (leftOperand instanceof JmcBooleanFormula left
                && rightOperand instanceof JmcBooleanFormula right) {
            return !left.concreteEvaluation() || right.concreteEvaluation();
        } else {
            throw new RuntimeException("Invalid operands for operator IMPLIES");
        }
    }

    /**
     * Evaluates the IFF (if and only if) operator.
     *
     * @return true if both operands are equal, false otherwise
     */
    private boolean evalIff() {
        if (leftOperand instanceof AbstractBoolean left
                && rightOperand instanceof AbstractBoolean right) {
            return getBoolValue(left) == getBoolValue(right);
        } else if (leftOperand instanceof JmcBooleanFormula left
                && rightOperand instanceof AbstractBoolean right) {
            return left.concreteEvaluation() == getBoolValue(right);
        } else if (leftOperand instanceof AbstractBoolean left
                && rightOperand instanceof JmcBooleanFormula right) {
            return getBoolValue(left) == right.concreteEvaluation();
        } else if (leftOperand instanceof JmcBooleanFormula left
                && rightOperand instanceof JmcBooleanFormula right) {
            return left.concreteEvaluation() == right.concreteEvaluation();
        } else {
            throw new RuntimeException("Invalid operands for operator IFF");
        }
    }

    /**
     * Evaluates the XOR operator.
     *
     * @return true if exactly one operand is true, false otherwise
     */
    private boolean evalXor() {
        if (leftOperand instanceof AbstractBoolean left
                && rightOperand instanceof AbstractBoolean right) {
            return getBoolValue(left) != getBoolValue(right);
        } else if (leftOperand instanceof JmcBooleanFormula left
                && rightOperand instanceof AbstractBoolean right) {
            return left.concreteEvaluation() != getBoolValue(right);
        } else if (leftOperand instanceof AbstractBoolean left
                && rightOperand instanceof JmcBooleanFormula right) {
            return getBoolValue(left) != right.concreteEvaluation();
        } else if (leftOperand instanceof JmcBooleanFormula left
                && rightOperand instanceof JmcBooleanFormula right) {
            return left.concreteEvaluation() != right.concreteEvaluation();
        } else {
            throw new RuntimeException("Invalid operands for operator XOR");
        }
    }

    /**
     * Retrieves the integer value from an AbstractInteger.
     *
     * @param abstractInteger the AbstractInteger to retrieve the value from
     * @return the integer value
     */
    public int getIntValue(AbstractInteger abstractInteger) {
        if (abstractInteger instanceof ConcreteInteger) {
            return abstractInteger.getValue();
        } else if (abstractInteger instanceof SymbolicInteger symbolicInteger) {
            return symbolicInteger.getIntValue();
        } else {
            throw new RuntimeException("Unsupported type of AbstractInteger");
        }
    }

    /**
     * Retrieves the boolean value from an AbstractBoolean.
     *
     * @param abstractBoolean the AbstractBoolean to retrieve the value from
     * @return the boolean value
     */
    public boolean getBoolValue(AbstractBoolean abstractBoolean) {
        if (abstractBoolean instanceof ConcreteBoolean) {
            return abstractBoolean.getValue();
        } else if (abstractBoolean instanceof SymbolicBoolean symbolicBoolean) {
            if (symbolicBoolean.getEval() != null) {
                return symbolicBoolean.getEval().concreteEvaluation();
            } else {
                return JmcSymbolic.getSymBoolVarValue(symbolicBoolean.getName());
            }
        } else {
            throw new RuntimeException("Unsupported type of AbstractBoolean");
        }
    }
}
