package org.mpi_sws.jmc.symbolic.bool;

import org.mpi_sws.jmc.symbolic.InstructionType;
import org.mpi_sws.jmc.symbolic.JmcSymbolic;
import org.sosy_lab.java_smt.api.*;

import java.util.HashMap;
import java.util.Map;

public class PropositionalFormula {

    private final BooleanFormulaManager bmgr;
    private final Map<String, BooleanFormula> booleanVariableMap =
            new HashMap<>();

    public PropositionalFormula() {
        bmgr = JmcSymbolic.getBmgr();
    }

    public JmcBooleanFormula not(JmcBooleanFormula op) {
        return makeUnaryOperation(op, InstructionType.NOT);
    }

    public JmcBooleanFormula not(SymbolicBoolean var) {
        return makeUnaryOperation(var, InstructionType.NOT);
    }

    public JmcBooleanFormula atomicLiteral(SymbolicBoolean var) {
        return makeUnaryOperation(var, InstructionType.ATOM);
    }

    public JmcBooleanFormula atomicLiteral(JmcBooleanFormula op) {
        return makeUnaryOperation(op, InstructionType.ATOM);
    }

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

    public JmcBooleanFormula and(JmcBooleanFormula op1, JmcBooleanFormula op2) {
        return makeBinaryOperation(op1, op2, InstructionType.AND);
    }

    public JmcBooleanFormula and(SymbolicBoolean var1, SymbolicBoolean var2) {
        return makeBinaryOperation(var1, var2, InstructionType.AND);
    }

    public JmcBooleanFormula and(JmcBooleanFormula op1, SymbolicBoolean var) {
        JmcBooleanFormula op2 = atomicLiteral(var);
        return makeBinaryOperation(op1, op2, InstructionType.AND);
    }

    public JmcBooleanFormula and(SymbolicBoolean var, JmcBooleanFormula op) {
        return and(var, op);
    }

    public JmcBooleanFormula or(JmcBooleanFormula op1, JmcBooleanFormula op2) {
        return makeBinaryOperation(op1, op2, InstructionType.OR);
    }

    public JmcBooleanFormula or(SymbolicBoolean var1, SymbolicBoolean var2) {
        return makeBinaryOperation(var1, var2, InstructionType.OR);
    }

    public JmcBooleanFormula or(JmcBooleanFormula op1, SymbolicBoolean var) {
        JmcBooleanFormula op2 = atomicLiteral(var);
        return makeBinaryOperation(op1, op2, InstructionType.OR);
    }

    public JmcBooleanFormula or(SymbolicBoolean var, JmcBooleanFormula op) {
        return or(var, op);
    }

    public JmcBooleanFormula implies(JmcBooleanFormula op1, JmcBooleanFormula op2) {
        return makeBinaryOperation(op1, op2, InstructionType.IMPLIES);
    }

    public JmcBooleanFormula implies(SymbolicBoolean var1, SymbolicBoolean var2) {
        return makeBinaryOperation(var1, var2, InstructionType.IMPLIES);
    }

    public JmcBooleanFormula implies(JmcBooleanFormula op1, SymbolicBoolean var) {
        JmcBooleanFormula op2 = atomicLiteral(var);
        return makeBinaryOperation(op1, op2, InstructionType.IMPLIES);
    }

    public JmcBooleanFormula implies(SymbolicBoolean var, JmcBooleanFormula op) {
        return implies(var, op);
    }

    public JmcBooleanFormula iff(JmcBooleanFormula op1, JmcBooleanFormula op2) {
        return makeBinaryOperation(op1, op2, InstructionType.IFF);
    }

    public JmcBooleanFormula iff(SymbolicBoolean var1, SymbolicBoolean var2) {
        return makeBinaryOperation(var1, var2, InstructionType.IFF);
    }

    public JmcBooleanFormula iff(JmcBooleanFormula op1, SymbolicBoolean var) {
        JmcBooleanFormula op2 = atomicLiteral(var);
        return makeBinaryOperation(op1, op2, InstructionType.IFF);
    }

    public JmcBooleanFormula iff(SymbolicBoolean var, JmcBooleanFormula op) {
        return iff(var, op);
    }

    public JmcBooleanFormula xor(JmcBooleanFormula op1, JmcBooleanFormula op2) {
        return makeBinaryOperation(op1, op2, InstructionType.XOR);
    }

    public JmcBooleanFormula xor(SymbolicBoolean var1, SymbolicBoolean var2) {
        return makeBinaryOperation(var1, var2, InstructionType.XOR);
    }

    public JmcBooleanFormula xor(JmcBooleanFormula op1, SymbolicBoolean var) {
        JmcBooleanFormula op2 = atomicLiteral(var);
        return makeBinaryOperation(op1, op2, InstructionType.XOR);
    }

    public JmcBooleanFormula xor(SymbolicBoolean var, JmcBooleanFormula op) {
        return xor(var, op);
    }

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

    private BooleanFormula makeBooleanFormula(
            SymbolicBoolean symbolicBoolean) {
        return findVariable(symbolicBoolean.getName());
    }

    private BooleanFormula findVariable(String name) {
        if (booleanVariableMap.containsKey(name)) {
            return booleanVariableMap.get(name);
        } else {
            SymBoolVariable symBoolVariable = JmcSymbolic.getSymBoolVariable(name);
            booleanVariableMap.put(name, symBoolVariable.getVar());
            return symBoolVariable.getVar();
        }
    }

    private Map<String, NumeralFormula.IntegerFormula> unionIntegerVariableMap(
            Map<String, NumeralFormula.IntegerFormula> map1,
            Map<String, NumeralFormula.IntegerFormula> map2) {
        Map<String, NumeralFormula.IntegerFormula> unionMap = new HashMap<>();
        unionMap.putAll(map1);
        unionMap.putAll(map2);
        return unionMap;
    }

    public Map<String, BooleanFormula> unionBooleanVariableMap(
            Map<String, BooleanFormula> map1,
            Map<String, BooleanFormula> map2) {
        Map<String, BooleanFormula> unionMap = new HashMap<>();
        unionMap.putAll(map1);
        unionMap.putAll(map2);
        return unionMap;
    }
}
