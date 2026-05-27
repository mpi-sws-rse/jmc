package org.mpi_sws.jmc.api.symbolic.bool;

import org.mpi_sws.jmc.api.symbolic.InstructionType;
import org.mpi_sws.jmc.solver.SolverUtil;
import org.sosy_lab.java_smt.api.*;

import java.util.HashMap;
import java.util.Map;

/**
 * This class provides methods to create and manipulate propositional formulas
 * using symbolic boolean variables.
 */
public class PropositionalFormula {

    /**
     * The BooleanFormulaManager instance used to create and manipulate boolean formulas.
     */
    private final BooleanFormulaManager bmgr;

    /**
     * A map to store symbolic boolean variables and their corresponding BooleanFormula instances.
     */
    private final Map<String, BooleanFormula> booleanVariableMap =
            new HashMap<>();

    /**
     * Constructs a PropositionalFormula instance and initializes the BooleanFormulaManager.
     */
    public PropositionalFormula() {
        bmgr = SolverUtil.getBmgr();
    }

    /**
     * Creates a boolean formula representing the negation of the given symbolic boolean formula.
     *
     * @param op the JmcBooleanFormula to negate
     * @return a new JmcBooleanFormula representing the negation
     */
    public JmcBooleanFormula not(JmcBooleanFormula op) {
        return makeUnaryOperation(op, InstructionType.NOT);
    }

    /**
     * Creates a boolean formula representing the negation of the given symbolic boolean variable.
     *
     * @param var the SymbolicBoolean variable to negate
     * @return a new JmcBooleanFormula representing the negation
     */
    public JmcBooleanFormula not(SymbolicBoolean var) {
        return makeUnaryOperation(var, InstructionType.NOT);
    }

    /**
     * Creates a boolean formula representing an atomic literal of the given symbolic boolean variable.
     *
     * @param var the SymbolicBoolean variable
     * @return a new JmcBooleanFormula representing the atomic literal
     */
    public JmcBooleanFormula atomicLiteral(SymbolicBoolean var) {
        return makeUnaryOperation(var, InstructionType.ATOM);
    }

    /**
     * Creates a boolean formula representing an atomic literal of the given JmcBooleanFormula.
     *
     * @param op the JmcBooleanFormula
     * @return a new JmcBooleanFormula representing the atomic literal
     */
    public JmcBooleanFormula atomicLiteral(JmcBooleanFormula op) {
        return makeUnaryOperation(op, InstructionType.ATOM);
    }

    /**
     * Helper method to create a unary operation on a JmcBooleanFormula based on the specified operation type.
     *
     * @param var       the JmcBooleanFormula operand
     * @param operation the type of unary operation to perform
     * @return a new JmcBooleanFormula representing the result of the unary operation
     */
    private JmcBooleanFormula makeUnaryOperation(JmcBooleanFormula var, InstructionType operation) {

        JmcBooleanFormula booleanFormula = new JmcBooleanFormula();

        switch (operation) {
            case ATOM -> {
                booleanFormula.setFormula(var.getFormula());
                break;
            }
            case NOT -> {
                BooleanFormula formula = bmgr.not(var.getFormula());
                booleanFormula.setFormula(formula);
                break;
            }
            default -> {
                throw new UnsupportedOperationException("Unsupported operation: " + operation);
            }
        }
        booleanFormula.setJmcFormula(var, null, operation);
        booleanFormula.setBooleanVariableMap(var.getBooleanVariableMap());
        booleanFormula.setIntegerVariableMap(var.getIntegerVariableMap());
        return booleanFormula;
    }

    /**
     * Helper method to create a unary operation on a SymbolicBoolean based on the specified operation type.
     *
     * @param var       the SymbolicBoolean operand
     * @param operation the type of unary operation to perform
     * @return a new JmcBooleanFormula representing the result of the unary operation
     */
    private JmcBooleanFormula makeUnaryOperation(SymbolicBoolean var, InstructionType operation) {
        SymbolicBoolean symBool = (SymbolicBoolean) var.read();
        if (symBool.getEval() != null) {
            return makeUnaryOperation(symBool.getEval(), operation);
        } else {
            booleanVariableMap.clear();
            JmcBooleanFormula booleanFormula = new JmcBooleanFormula();

            switch (operation) {
                case ATOM -> {
                    booleanFormula.setFormula(makeBooleanFormula(symBool));
                    break;
                }
                case NOT -> {
                    BooleanFormula formula =
                            bmgr.not(makeBooleanFormula(symBool));
                    booleanFormula.setFormula(formula);
                    break;
                }
                default -> {
                    throw new UnsupportedOperationException("Unsupported operation: " + operation);
                }
            }
            booleanFormula.setJmcFormula(symBool, null, operation);
            booleanFormula.setBooleanVariableMap(booleanVariableMap);
            return booleanFormula;
        }
    }

    /**
     * Creates a boolean formula representing the conjunction (AND) of two symbolic boolean formulas.
     *
     * @param op1 the first JmcBooleanFormula
     * @param op2 the second JmcBooleanFormula
     * @return a new JmcBooleanFormula representing the conjunction
     */
    public JmcBooleanFormula and(JmcBooleanFormula op1, JmcBooleanFormula op2) {
        return makeBinaryOperation(op1, op2, InstructionType.AND);
    }

    /**
     * Creates a boolean formula representing the conjunction (AND) of two symbolic boolean variables.
     *
     * @param var1 the first SymbolicBoolean variable
     * @param var2 the second SymbolicBoolean variable
     * @return a new JmcBooleanFormula representing the conjunction
     */
    public JmcBooleanFormula and(SymbolicBoolean var1, SymbolicBoolean var2) {
        return makeBinaryOperation(var1, var2, InstructionType.AND);
    }

    /**
     * Creates a boolean formula representing the conjunction (AND) of a symbolic boolean formula and
     * a symbolic boolean variable.
     *
     * @param op1 the JmcBooleanFormula
     * @param var the SymbolicBoolean variable
     * @return a new JmcBooleanFormula representing the conjunction
     */
    public JmcBooleanFormula and(JmcBooleanFormula op1, SymbolicBoolean var) {
        JmcBooleanFormula op2 = atomicLiteral(var);
        return makeBinaryOperation(op1, op2, InstructionType.AND);
    }

    /**
     * Creates a boolean formula representing the conjunction (AND) of a symbolic boolean variable and
     * a symbolic boolean formula.
     *
     * @param var the SymbolicBoolean variable
     * @param op  the JmcBooleanFormula
     * @return a new JmcBooleanFormula representing the conjunction
     */
    public JmcBooleanFormula and(SymbolicBoolean var, JmcBooleanFormula op) {
        return and(op, var);
    }

    /**
     * Creates a boolean formula representing the disjunction (OR) of two symbolic boolean formulas.
     *
     * @param op1 the first JmcBooleanFormula
     * @param op2 the second JmcBooleanFormula
     * @return a new JmcBooleanFormula representing the disjunction
     */
    public JmcBooleanFormula or(JmcBooleanFormula op1, JmcBooleanFormula op2) {
        return makeBinaryOperation(op1, op2, InstructionType.OR);
    }

    /**
     * Creates a boolean formula representing the disjunction (OR) of two symbolic boolean variables.
     *
     * @param var1 the first SymbolicBoolean variable
     * @param var2 the second SymbolicBoolean variable
     * @return a new JmcBooleanFormula representing the disjunction
     */
    public JmcBooleanFormula or(SymbolicBoolean var1, SymbolicBoolean var2) {
        return makeBinaryOperation(var1, var2, InstructionType.OR);
    }

    /**
     * Creates a boolean formula representing the disjunction (OR) of a symbolic boolean formula and
     * a symbolic boolean variable.
     *
     * @param op1 the JmcBooleanFormula
     * @param var the SymbolicBoolean variable
     * @return a new JmcBooleanFormula representing the disjunction
     */
    public JmcBooleanFormula or(JmcBooleanFormula op1, SymbolicBoolean var) {
        JmcBooleanFormula op2 = atomicLiteral(var);
        return makeBinaryOperation(op1, op2, InstructionType.OR);
    }

    /**
     * Creates a boolean formula representing the disjunction (OR) of a symbolic boolean variable and
     * a symbolic boolean formula.
     *
     * @param var the SymbolicBoolean variable
     * @param op  the JmcBooleanFormula
     * @return a new JmcBooleanFormula representing the disjunction
     */
    public JmcBooleanFormula or(SymbolicBoolean var, JmcBooleanFormula op) {
        return or(op, var);
    }

    /**
     * Creates a boolean formula representing the implication (IMPLIES) of two symbolic boolean formulas.
     *
     * @param op1 the first JmcBooleanFormula
     * @param op2 the second JmcBooleanFormula
     * @return a new JmcBooleanFormula representing the implication
     */
    public JmcBooleanFormula implies(JmcBooleanFormula op1, JmcBooleanFormula op2) {
        return makeBinaryOperation(op1, op2, InstructionType.IMPLIES);
    }

    /**
     * Creates a boolean formula representing the implication (IMPLIES) of two symbolic boolean variables.
     *
     * @param var1 the first SymbolicBoolean variable
     * @param var2 the second SymbolicBoolean variable
     * @return a new JmcBooleanFormula representing the implication
     */
    public JmcBooleanFormula implies(SymbolicBoolean var1, SymbolicBoolean var2) {
        return makeBinaryOperation(var1, var2, InstructionType.IMPLIES);
    }

    /**
     * Creates a boolean formula representing the implication (IMPLIES) of a symbolic boolean formula and
     * a symbolic boolean variable.
     *
     * @param op1 the JmcBooleanFormula
     * @param var the SymbolicBoolean variable
     * @return a new JmcBooleanFormula representing the implication
     */
    public JmcBooleanFormula implies(JmcBooleanFormula op1, SymbolicBoolean var) {
        JmcBooleanFormula op2 = atomicLiteral(var);
        return makeBinaryOperation(op1, op2, InstructionType.IMPLIES);
    }

    /**
     * Creates a boolean formula representing the implication (IMPLIES) of a symbolic boolean variable and
     * a symbolic boolean formula.
     *
     * @param var the SymbolicBoolean variable
     * @param op  the JmcBooleanFormula
     * @return a new JmcBooleanFormula representing the implication
     */
    public JmcBooleanFormula implies(SymbolicBoolean var, JmcBooleanFormula op) {
        return implies(op, var);
    }

    /**
     * Creates a boolean formula representing the biconditional (IFF) of two symbolic boolean formulas.
     *
     * @param op1 the first JmcBooleanFormula
     * @param op2 the second JmcBooleanFormula
     * @return a new JmcBooleanFormula representing the biconditional
     */
    public JmcBooleanFormula iff(JmcBooleanFormula op1, JmcBooleanFormula op2) {
        return makeBinaryOperation(op1, op2, InstructionType.IFF);
    }

    /**
     * Creates a boolean formula representing the biconditional (IFF) of two symbolic boolean variables.
     *
     * @param var1 the first SymbolicBoolean variable
     * @param var2 the second SymbolicBoolean variable
     * @return a new JmcBooleanFormula representing the biconditional
     */
    public JmcBooleanFormula iff(SymbolicBoolean var1, SymbolicBoolean var2) {
        return makeBinaryOperation(var1, var2, InstructionType.IFF);
    }

    /**
     * Creates a boolean formula representing the biconditional (IFF) of a symbolic boolean formula and
     * a symbolic boolean variable.
     *
     * @param op1 the JmcBooleanFormula
     * @param var the SymbolicBoolean variable
     * @return a new JmcBooleanFormula representing the biconditional
     */
    public JmcBooleanFormula iff(JmcBooleanFormula op1, SymbolicBoolean var) {
        JmcBooleanFormula op2 = atomicLiteral(var);
        return makeBinaryOperation(op1, op2, InstructionType.IFF);
    }

    /**
     * Creates a boolean formula representing the biconditional (IFF) of a symbolic boolean variable and
     * a symbolic boolean formula.
     *
     * @param var the SymbolicBoolean variable
     * @param op  the JmcBooleanFormula
     * @return a new JmcBooleanFormula representing the biconditional
     */
    public JmcBooleanFormula iff(SymbolicBoolean var, JmcBooleanFormula op) {
        return iff(op, var);
    }

    /**
     * Creates a boolean formula representing the exclusive disjunction (XOR) of two symbolic boolean formulas.
     *
     * @param op1 the first JmcBooleanFormula
     * @param op2 the second JmcBooleanFormula
     * @return a new JmcBooleanFormula representing the exclusive disjunction
     */
    public JmcBooleanFormula xor(JmcBooleanFormula op1, JmcBooleanFormula op2) {
        return makeBinaryOperation(op1, op2, InstructionType.XOR);
    }

    /**
     * Creates a boolean formula representing the exclusive disjunction (XOR) of two symbolic boolean variables.
     *
     * @param var1 the first SymbolicBoolean variable
     * @param var2 the second SymbolicBoolean variable
     * @return a new JmcBooleanFormula representing the exclusive disjunction
     */
    public JmcBooleanFormula xor(SymbolicBoolean var1, SymbolicBoolean var2) {
        return makeBinaryOperation(var1, var2, InstructionType.XOR);
    }

    /**
     * Creates a boolean formula representing the exclusive disjunction (XOR) of a symbolic boolean formula and
     * a symbolic boolean variable.
     *
     * @param op1 the JmcBooleanFormula
     * @param var the SymbolicBoolean variable
     * @return a new JmcBooleanFormula representing the exclusive disjunction
     */
    public JmcBooleanFormula xor(JmcBooleanFormula op1, SymbolicBoolean var) {
        JmcBooleanFormula op2 = atomicLiteral(var);
        return makeBinaryOperation(op1, op2, InstructionType.XOR);
    }

    /**
     * Creates a boolean formula representing the exclusive disjunction (XOR) of a symbolic boolean variable and
     * a symbolic boolean formula.
     *
     * @param var the SymbolicBoolean variable
     * @param op  the JmcBooleanFormula
     * @return a new JmcBooleanFormula representing the exclusive disjunction
     */
    public JmcBooleanFormula xor(SymbolicBoolean var, JmcBooleanFormula op) {
        return xor(op, var);
    }

    /**
     * Helper method to create a binary operation on two JmcBooleanFormulas based on the specified operation type.
     *
     * @param left      the left JmcBooleanFormula operand
     * @param right     the right JmcBooleanFormula operand
     * @param operation the type of binary operation to perform
     * @return a new JmcBooleanFormula representing the result of the binary operation
     */
    private JmcBooleanFormula makeBinaryOperation(
            JmcBooleanFormula left, JmcBooleanFormula right, InstructionType operation) {
        JmcBooleanFormula booleanFormula = new JmcBooleanFormula();
        BooleanFormula formula;

        switch (operation) {
            case AND -> {
                formula = bmgr.and(left.getFormula(), right.getFormula());
                break;
            }
            case OR -> {
                formula = bmgr.or(left.getFormula(), right.getFormula());
                break;
            }
            case IMPLIES -> {
                formula = bmgr.implication(left.getFormula(), right.getFormula());
                break;
            }
            case IFF -> {
                formula = bmgr.equivalence(left.getFormula(), right.getFormula());
                break;
            }
            case XOR -> {
                formula = bmgr.xor(left.getFormula(), right.getFormula());
                break;
            }
            default -> {
                throw new UnsupportedOperationException("Unsupported operation: " + operation);
            }
        }
        booleanFormula.setFormula(formula);
        booleanFormula.setJmcFormula(left, right, operation);
        booleanFormula.setIntegerVariableMap(
                unionIntegerVariableMap(
                        left.getIntegerVariableMap(), right.getIntegerVariableMap()));
        booleanFormula.setBooleanVariableMap(
                unionBooleanVariableMap(
                        left.getBooleanVariableMap(), right.getBooleanVariableMap()));
        return booleanFormula;
    }

    /**
     * Helper method to create a binary operation on two SymbolicBooleans based on the specified operation type.
     *
     * @param left      the left SymbolicBoolean operand
     * @param right     the right SymbolicBoolean operand
     * @param operation the type of binary operation to perform
     * @return a new JmcBooleanFormula representing the result of the binary operation
     */
    private JmcBooleanFormula makeBinaryOperation(
            SymbolicBoolean left, SymbolicBoolean right, InstructionType operation) {

        if (left.getEval() != null && right.getEval() != null) {
            SymbolicBoolean symbol1 = (SymbolicBoolean) left.read();
            SymbolicBoolean symbol2 = (SymbolicBoolean) right.read();

            return makeBinaryOperation(symbol1, symbol2, operation);
        } else if (left.getEval() != null) {
            SymbolicBoolean symbol1 = (SymbolicBoolean) left.read();
            JmcBooleanFormula symbol2 = atomicLiteral(right);

            return makeBinaryOperation(symbol1.getEval(), symbol2, operation);
        } else if (right.getEval() != null) {
            SymbolicBoolean symbol2 = (SymbolicBoolean) right.read();
            JmcBooleanFormula symbol1 = atomicLiteral(left);

            return makeBinaryOperation(symbol1, symbol2.getEval(), operation);
        } else {
            booleanVariableMap.clear();
            JmcBooleanFormula boolFormula = new JmcBooleanFormula();
            SymbolicBoolean symbol1 = (SymbolicBoolean) left.read();
            SymbolicBoolean symbol2 = (SymbolicBoolean) right.read();
            BooleanFormula leftOperand = makeBooleanFormula(symbol1);
            BooleanFormula rightOperand = makeBooleanFormula(symbol2);
            BooleanFormula formula;
            switch (operation) {
                case AND -> {
                    formula = bmgr.and(leftOperand, rightOperand);
                    break;
                }
                case OR -> {
                    formula = bmgr.or(leftOperand, rightOperand);
                    break;
                }
                case IMPLIES -> {
                    formula = bmgr.implication(leftOperand, rightOperand);
                    break;
                }
                case IFF -> {
                    formula = bmgr.equivalence(leftOperand, rightOperand);
                    break;
                }
                case XOR -> {
                    formula = bmgr.xor(leftOperand, rightOperand);
                    break;
                }
                default -> {
                    throw new UnsupportedOperationException("Unsupported operation: " + operation);
                }
            }
            boolFormula.setFormula(formula);
            boolFormula.setJmcFormula(symbol1, symbol2, operation);
            boolFormula.setBooleanVariableMap(booleanVariableMap);
            return boolFormula;
        }
    }

    /**
     * Finds a BooleanFormula for the given SymbolicBoolean.
     *
     * @param symbolicBoolean the SymbolicBoolean to find the BooleanFormula for
     * @return the corresponding BooleanFormula
     */
    private BooleanFormula makeBooleanFormula(
            SymbolicBoolean symbolicBoolean) {
        return findVariable(symbolicBoolean.getName());
    }

    /**
     * Finds or creates a BooleanFormula for the given variable name.
     *
     * @param name the name of the variable
     * @return the corresponding BooleanFormula
     */
    private BooleanFormula findVariable(String name) {
        if (booleanVariableMap.containsKey(name)) {
            return booleanVariableMap.get(name);
        } else {
            SymBoolVariable symBoolVariable = SolverUtil.getSymBoolVariable(name);
            booleanVariableMap.put(name, symBoolVariable.getVar());
            return symBoolVariable.getVar();
        }
    }

    /**
     * Merges two maps of integer variables into a single map.
     *
     * @param map1 the first map of integer variables
     * @param map2 the second map of integer variables
     * @return a new map containing all entries from both input maps
     */
    private Map<String, NumeralFormula.IntegerFormula> unionIntegerVariableMap(
            Map<String, NumeralFormula.IntegerFormula> map1,
            Map<String, NumeralFormula.IntegerFormula> map2) {
        Map<String, NumeralFormula.IntegerFormula> unionMap = new HashMap<>();
        unionMap.putAll(map1);
        unionMap.putAll(map2);
        return unionMap;
    }

    /**
     * Merges two maps of boolean variables into a single map.
     *
     * @param map1 the first map of boolean variables
     * @param map2 the second map of boolean variables
     * @return a new map containing all entries from both input maps
     */
    public Map<String, BooleanFormula> unionBooleanVariableMap(
            Map<String, BooleanFormula> map1,
            Map<String, BooleanFormula> map2) {
        Map<String, BooleanFormula> unionMap = new HashMap<>();
        unionMap.putAll(map1);
        unionMap.putAll(map2);
        return unionMap;
    }
}
