package org.mpisws.symbolic;

import org.mpisws.runtime.RuntimeEnvironment;

public class JMCFormula {

    public SymbolicOperand leftOperand;

    public SymbolicOperand rightOperand;

    public InstructionType operator;

    public void setLeftOperand(SymbolicOperand leftOperand) {
        this.leftOperand = leftOperand;
    }

    public void setRightOperand(SymbolicOperand rightOperand) {
        this.rightOperand = rightOperand;
    }

    public void setOperator(InstructionType operator) {
        this.operator = operator;
    }

    public boolean evaluate() {
        if (operator == null) {
            throw new IllegalArgumentException("[JMC Formula Message] Operator is not set");
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
        } else {
            throw new IllegalArgumentException("[JMC Formula Message] Unsupported operator");
        }

    }

    private boolean evalAtom() {
        if (rightOperand != null) {
            throw new IllegalArgumentException("[JMC Formula Message] Right operand must be null for ATOM operator");
        } else if (leftOperand instanceof AbstractBoolean left) {
            return getBoolValue(left);
        } else if (leftOperand instanceof SymbolicOperation left) {
            return left.concreteEvaluation();
        } else {
            throw new IllegalArgumentException("[JMC Formula Message] Invalid operand for operator ATOM");
        }
    }

    private boolean evalNot() {
        if (rightOperand != null) {
            throw new IllegalArgumentException("[JMC Formula Message] Right operand must be null for NOT operator");
        } else if (leftOperand instanceof AbstractBoolean left) {
            return !getBoolValue(left);
        } else if (leftOperand instanceof SymbolicOperation left) {
            return !left.concreteEvaluation();
        } else {
            throw new IllegalArgumentException("[JMC Formula Message] Invalid operand for operator NOT");
        }
    }

    private boolean evalEqual() {
        if (leftOperand instanceof AbstractInteger left && rightOperand instanceof AbstractInteger right) {
            return getIntValue(left) == getIntValue(right);
        } else {
            throw new IllegalArgumentException("[JMC Formula Message] Invalid operands for operator EQ");
        }
    }

    private boolean evalNeq() {
        if (leftOperand instanceof AbstractInteger left && rightOperand instanceof AbstractInteger right) {
            return getIntValue(left) != getIntValue(right);
        } else {
            throw new IllegalArgumentException("[JMC Formula Message] Invalid operands for operator NEQ");
        }
    }

    private boolean evalGreater() {
        if (leftOperand instanceof AbstractInteger left && rightOperand instanceof AbstractInteger right) {
            return getIntValue(left) > getIntValue(right);
        } else {
            throw new IllegalArgumentException("[JMC Formula Message] Invalid operands for operator GT");
        }
    }

    private boolean evalLess() {
        if (leftOperand instanceof AbstractInteger left && rightOperand instanceof AbstractInteger right) {
            return getIntValue(left) < getIntValue(right);
        } else {
            throw new IllegalArgumentException("[JMC Formula Message] Invalid operands for operator LT");
        }
    }

    private boolean evalLessEqual() {
        if (leftOperand instanceof AbstractInteger left && rightOperand instanceof AbstractInteger right) {
            return getIntValue(left) <= getIntValue(right);
        } else {
            throw new IllegalArgumentException("[JMC Formula Message] Invalid operands for operator LEQ");
        }
    }

    private boolean evalGreaterEqual() {
        if (leftOperand instanceof AbstractInteger left && rightOperand instanceof AbstractInteger right) {
            return getIntValue(left) >= getIntValue(right);
        } else {
            throw new IllegalArgumentException("[JMC Formula Message] Invalid operands for operator GEQ");
        }
    }

    private boolean evalAnd() {
        if (leftOperand instanceof AbstractBoolean left && rightOperand instanceof AbstractBoolean right) {
            return getBoolValue(left) && getBoolValue(right);
        } else if (leftOperand instanceof SymbolicOperation left && rightOperand instanceof AbstractBoolean right) {
            return left.concreteEvaluation() && getBoolValue(right);
        } else if (leftOperand instanceof AbstractBoolean left && rightOperand instanceof SymbolicOperation right) {
            return getBoolValue(left) && right.concreteEvaluation();
        } else if (leftOperand instanceof SymbolicOperation left && rightOperand instanceof SymbolicOperation right) {
            return left.concreteEvaluation() && right.concreteEvaluation();
        } else {
            throw new IllegalArgumentException("[JMC Formula Message] Invalid operands for operator AND");
        }
    }

    private boolean evalOr() {
        if (leftOperand instanceof AbstractBoolean left && rightOperand instanceof AbstractBoolean right) {
            return getBoolValue(left) || getBoolValue(right);
        } else if (leftOperand instanceof SymbolicOperation left && rightOperand instanceof AbstractBoolean right) {
            return left.concreteEvaluation() || getBoolValue(right);
        } else if (leftOperand instanceof AbstractBoolean left && rightOperand instanceof SymbolicOperation right) {
            return getBoolValue(left) || right.concreteEvaluation();
        } else if (leftOperand instanceof SymbolicOperation left && rightOperand instanceof SymbolicOperation right) {
            return left.concreteEvaluation() || right.concreteEvaluation();
        } else {
            throw new IllegalArgumentException("[JMC Formula Message] Invalid operands for operator OR");
        }
    }

    private boolean evalImplies() {
        if (leftOperand instanceof AbstractBoolean left && rightOperand instanceof AbstractBoolean right) {
            return !getBoolValue(left) || getBoolValue(right);
        } else if (leftOperand instanceof SymbolicOperation left && rightOperand instanceof AbstractBoolean right) {
            return !left.concreteEvaluation() || getBoolValue(right);
        } else if (leftOperand instanceof AbstractBoolean left && rightOperand instanceof SymbolicOperation right) {
            return !getBoolValue(left) || right.concreteEvaluation();
        } else if (leftOperand instanceof SymbolicOperation left && rightOperand instanceof SymbolicOperation right) {
            return !left.concreteEvaluation() || right.concreteEvaluation();
        } else {
            throw new IllegalArgumentException("[JMC Formula Message] Invalid operands for operator IMPLIES");
        }
    }

    private boolean evalIff() {
        if (leftOperand instanceof AbstractBoolean left && rightOperand instanceof AbstractBoolean right) {
            return getBoolValue(left) == getBoolValue(right);
        } else if (leftOperand instanceof SymbolicOperation left && rightOperand instanceof AbstractBoolean right) {
            return left.concreteEvaluation() == getBoolValue(right);
        } else if (leftOperand instanceof AbstractBoolean left && rightOperand instanceof SymbolicOperation right) {
            return getBoolValue(left) == right.concreteEvaluation();
        } else if (leftOperand instanceof SymbolicOperation left && rightOperand instanceof SymbolicOperation right) {
            return left.concreteEvaluation() == right.concreteEvaluation();
        } else {
            throw new IllegalArgumentException("[JMC Formula Message] Invalid operands for operator IFF");
        }
    }

    private boolean evalXor() {
        if (leftOperand instanceof AbstractBoolean left && rightOperand instanceof AbstractBoolean right) {
            return getBoolValue(left) != getBoolValue(right);
        } else if (leftOperand instanceof SymbolicOperation left && rightOperand instanceof AbstractBoolean right) {
            return left.concreteEvaluation() != getBoolValue(right);
        } else if (leftOperand instanceof AbstractBoolean left && rightOperand instanceof SymbolicOperation right) {
            return getBoolValue(left) != right.concreteEvaluation();
        } else if (leftOperand instanceof SymbolicOperation left && rightOperand instanceof SymbolicOperation right) {
            return left.concreteEvaluation() != right.concreteEvaluation();
        } else {
            throw new IllegalArgumentException("[JMC Formula Message] Invalid operands for operator XOR");
        }
    }

    public int getIntValue(AbstractInteger abstractInteger) {
        if (abstractInteger instanceof ConcreteInteger) {
            return abstractInteger.getValue();
        } else if (abstractInteger instanceof SymbolicInteger symbolicInteger) {
            if (symbolicInteger.getEval() != null) {
                int leftValue = getIntValue(symbolicInteger.getEval().getLeft());
                int rightValue = getIntValue(symbolicInteger.getEval().getRight());
                switch (symbolicInteger.getEval().getOperator()) {
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
                        throw new IllegalArgumentException("[JMC Formula Message] Unsupported operator");
                }
            } else {
                return RuntimeEnvironment.solver.getSymIntVarValue(symbolicInteger.getName());
            }
        } else {
            throw new IllegalArgumentException("[JMC Formula Message] Unsupported type of AbstractInteger");
        }
    }

    public boolean getBoolValue(AbstractBoolean abstractBoolean) {
        if (abstractBoolean instanceof ConcreteBoolean) {
            return abstractBoolean.getValue();
        } else if (abstractBoolean instanceof SymbolicBoolean symbolicBoolean) {
            if (symbolicBoolean.getEval() != null) {
                return symbolicBoolean.getEval().concreteEvaluation();
            } else {
                return RuntimeEnvironment.solver.getSymBoolVarValue(symbolicBoolean.getName());
            }
        } else {
            throw new IllegalArgumentException("[JMC Formula Message] Unsupported type of AbstractBoolean");
        }
    }
}