package org.mpisws.symbolic;

import org.mpisws.runtime.JmcRuntime;
import org.mpisws.solver.SymbolicSolver;
import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.java_smt.api.*;
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula;

import java.util.HashMap;
import java.util.Map;

public class PropositionalFormula {

    private final Configuration config;
    private final LogManager logger;
    private final ShutdownManager shutdown;
    private final SolverContext context;
    private final FormulaManager fmgr;
    private final BooleanFormulaManager bmgr;
    private final IntegerFormulaManager imgr;
    private final Map<String, BooleanFormula> booleanVariableMap = new HashMap<>();
    private final SymbolicSolver solver = JmcRuntime.solver;

    public PropositionalFormula() {
        config = JmcRuntime.solver.getConfig();
        logger = JmcRuntime.solver.getLogger();
        shutdown = JmcRuntime.solver.getShutdown();
        context = JmcRuntime.solver.getContext();
        fmgr = JmcRuntime.solver.getFmgr();
        bmgr = JmcRuntime.solver.getBmgr();
        imgr = JmcRuntime.solver.getImgr();
    }

    public SymbolicOperation atomicLiteral(SymbolicBoolean var) {
        if (var.getEval() != null) {
            SymbolicBoolean symbol = (SymbolicBoolean) var.read();
            return atomicLiteral(symbol.getEval());
        } else {
            booleanVariableMap.clear();
            SymbolicOperation symbolicOperation = new SymbolicOperation();
            SymbolicBoolean symbol = (SymbolicBoolean) var.read();
            BooleanFormula formula = makeBooleanFormula(symbol);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setJmcFormula(symbol, null, InstructionType.ATOM);
            symbolicOperation.setBooleanVariableMap(booleanVariableMap);
            return symbolicOperation;
        }
    }

    public SymbolicOperation atomicLiteral(SymbolicOperation op) {
        SymbolicOperation symbolicOperation = new SymbolicOperation();
        symbolicOperation.setFormula(op.getFormula());
        symbolicOperation.setJmcFormula(op, null, InstructionType.ATOM);
        symbolicOperation.setIntegerVariableMap(op.getIntegerVariableMap());
        symbolicOperation.setBooleanVariableMap(op.getBooleanVariableMap());
        return symbolicOperation;
    }

    public SymbolicOperation and(SymbolicOperation op1, SymbolicOperation op2) {
        SymbolicOperation symbolicOperation = new SymbolicOperation();
        BooleanFormula formula = bmgr.and(op1.getFormula(), op2.getFormula());
        symbolicOperation.setFormula(formula);
        symbolicOperation.setJmcFormula(op1, op2, InstructionType.AND);
        symbolicOperation.setIntegerVariableMap(
                unionIntegerVariableMap(op1.getIntegerVariableMap(), op2.getIntegerVariableMap()));
        symbolicOperation.setBooleanVariableMap(
                unionBooleanVariableMap(op1.getBooleanVariableMap(), op2.getBooleanVariableMap()));
        return symbolicOperation;
    }

    public SymbolicOperation and(SymbolicBoolean var1, SymbolicBoolean var2) {
        if (var1.getEval() != null && var2.getEval() != null) {
            SymbolicBoolean symbol1 = (SymbolicBoolean) var1.read();
            SymbolicBoolean symbol2 = (SymbolicBoolean) var2.read();
            return and(symbol1.getEval(), symbol2.getEval());
        } else if (var1.getEval() != null) {
            SymbolicBoolean symbol1 = (SymbolicBoolean) var1.read();
            return and(symbol1.getEval(), var2);
        } else if (var2.getEval() != null) {
            SymbolicBoolean symbol2 = (SymbolicBoolean) var2.read();
            return and(var1, symbol2.getEval());
        } else {
            booleanVariableMap.clear();
            SymbolicOperation symbolicOperation = new SymbolicOperation();
            SymbolicBoolean symbol1 = (SymbolicBoolean) var1.read();
            SymbolicBoolean symbol2 = (SymbolicBoolean) var2.read();
            BooleanFormula leftOperand = makeBooleanFormula(symbol1);
            BooleanFormula rightOperand = makeBooleanFormula(symbol2);
            BooleanFormula formula = bmgr.and(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setJmcFormula(symbol1, symbol2, InstructionType.AND);
            symbolicOperation.setBooleanVariableMap(booleanVariableMap);
            return symbolicOperation;
        }
    }

    public SymbolicOperation and(SymbolicOperation op1, SymbolicBoolean var) {
        // SymbolicBoolean symbolicBoolean = (SymbolicBoolean) var.read();
        // SymbolicOperation op2 = makeSymbolicOperation(symbolicBoolean);
        SymbolicOperation op2 = atomicLiteral(var);
        return and(op1, op2);
    }

    public SymbolicOperation and(SymbolicBoolean var, SymbolicOperation op) {
        return and(op, var);
    }

    public SymbolicOperation or(SymbolicOperation op1, SymbolicOperation op2) {
        SymbolicOperation symbolicOperation = new SymbolicOperation();
        BooleanFormula formula = bmgr.or(op1.getFormula(), op2.getFormula());
        symbolicOperation.setFormula(formula);
        symbolicOperation.setJmcFormula(op1, op2, InstructionType.OR);
        symbolicOperation.setIntegerVariableMap(
                unionIntegerVariableMap(op1.getIntegerVariableMap(), op2.getIntegerVariableMap()));
        symbolicOperation.setBooleanVariableMap(
                unionBooleanVariableMap(op1.getBooleanVariableMap(), op2.getBooleanVariableMap()));
        return symbolicOperation;
    }

    public SymbolicOperation or(SymbolicBoolean var1, SymbolicBoolean var2) {
        if (var1.getEval() != null && var2.getEval() != null) {
            SymbolicBoolean symbol1 = (SymbolicBoolean) var1.read();
            SymbolicBoolean symbol2 = (SymbolicBoolean) var2.read();
            return or(symbol1.getEval(), symbol2.getEval());
        } else if (var1.getEval() != null) {
            SymbolicBoolean symbol1 = (SymbolicBoolean) var1.read();
            return or(symbol1.getEval(), var2);
        } else if (var2.getEval() != null) {
            SymbolicBoolean symbol2 = (SymbolicBoolean) var2.read();
            return or(var1, symbol2.getEval());
        } else {
            booleanVariableMap.clear();
            SymbolicBoolean symbol1 = (SymbolicBoolean) var1.read();
            SymbolicBoolean symbol2 = (SymbolicBoolean) var2.read();
            SymbolicOperation symbolicOperation = new SymbolicOperation();
            BooleanFormula leftOperand = makeBooleanFormula(symbol1);
            BooleanFormula rightOperand = makeBooleanFormula(symbol2);
            BooleanFormula formula = bmgr.or(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setJmcFormula(symbol1, symbol2, InstructionType.OR);
            symbolicOperation.setBooleanVariableMap(booleanVariableMap);
            return symbolicOperation;
        }
    }

    public SymbolicOperation or(SymbolicOperation op1, SymbolicBoolean var) {
        // SymbolicBoolean symbolicBoolean = (SymbolicBoolean) var.read();
        // SymbolicOperation op2 = makeSymbolicOperation(symbolicBoolean);
        SymbolicOperation op2 = atomicLiteral(var);
        return or(op1, op2);
    }

    public SymbolicOperation or(SymbolicBoolean var, SymbolicOperation op) {
        return or(op, var);
    }

    public SymbolicOperation not(SymbolicOperation op) {
        SymbolicOperation symbolicOperation = new SymbolicOperation();
        BooleanFormula formula = bmgr.not(op.getFormula());
        symbolicOperation.setFormula(formula);
        symbolicOperation.setJmcFormula(op, null, InstructionType.NOT);
        symbolicOperation.setIntegerVariableMap(op.getIntegerVariableMap());
        symbolicOperation.setBooleanVariableMap(op.getBooleanVariableMap());
        return symbolicOperation;
    }

    public SymbolicOperation not(SymbolicBoolean var) {
        SymbolicBoolean symbol = (SymbolicBoolean) var.read();
        if (symbol.getEval() != null) {
            return not(symbol.getEval());
        } else {
            booleanVariableMap.clear();
            SymbolicOperation symbolicOperation = new SymbolicOperation();
            BooleanFormula formula = bmgr.not(makeBooleanFormula(symbol));
            symbolicOperation.setFormula(formula);
            symbolicOperation.setJmcFormula(symbol, null, InstructionType.NOT);
            symbolicOperation.setBooleanVariableMap(booleanVariableMap);
            return symbolicOperation;
        }
    }

    public SymbolicOperation implies(SymbolicOperation op1, SymbolicOperation op2) {
        SymbolicOperation symbolicOperation = new SymbolicOperation();
        BooleanFormula formula = bmgr.implication(op1.getFormula(), op2.getFormula());
        symbolicOperation.setFormula(formula);
        symbolicOperation.setJmcFormula(op1, op2, InstructionType.IMPLIES);
        symbolicOperation.setIntegerVariableMap(
                unionIntegerVariableMap(op1.getIntegerVariableMap(), op2.getIntegerVariableMap()));
        symbolicOperation.setBooleanVariableMap(
                unionBooleanVariableMap(op1.getBooleanVariableMap(), op2.getBooleanVariableMap()));
        return symbolicOperation;
    }

    public SymbolicOperation implies(SymbolicBoolean var1, SymbolicBoolean var2) {
        if (var1.getEval() != null && var2.getEval() != null) {
            SymbolicBoolean symbol1 = (SymbolicBoolean) var1.read();
            SymbolicBoolean symbol2 = (SymbolicBoolean) var2.read();
            return implies(symbol1.getEval(), symbol2.getEval());
        } else if (var1.getEval() != null) {
            SymbolicBoolean symbol1 = (SymbolicBoolean) var1.read();
            return implies(symbol1.getEval(), var2);
        } else if (var2.getEval() != null) {
            SymbolicBoolean symbol2 = (SymbolicBoolean) var2.read();
            return implies(var1, symbol2.getEval());
        } else {
            booleanVariableMap.clear();
            SymbolicOperation symbolicOperation = new SymbolicOperation();
            SymbolicBoolean symbol1 = (SymbolicBoolean) var1.read();
            SymbolicBoolean symbol2 = (SymbolicBoolean) var2.read();
            BooleanFormula leftOperand = makeBooleanFormula(symbol1);
            BooleanFormula rightOperand = makeBooleanFormula(symbol2);
            BooleanFormula formula = bmgr.implication(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setJmcFormula(symbol1, symbol2, InstructionType.IMPLIES);
            symbolicOperation.setBooleanVariableMap(booleanVariableMap);
            return symbolicOperation;
        }
    }

    public SymbolicOperation implies(SymbolicOperation op, SymbolicBoolean var) {
        // SymbolicBoolean symbol = (SymbolicBoolean) var.read();
        // SymbolicOperation op2 = makeSymbolicOperation(symbol);
        SymbolicOperation op2 = atomicLiteral(var);
        return implies(op, op2);
    }

    public SymbolicOperation implies(SymbolicBoolean var, SymbolicOperation op) {
        return implies(op, var);
    }

    public SymbolicOperation iff(SymbolicOperation op1, SymbolicOperation op2) {
        SymbolicOperation symbolicOperation = new SymbolicOperation();
        BooleanFormula formula = bmgr.equivalence(op1.getFormula(), op2.getFormula());
        symbolicOperation.setFormula(formula);
        symbolicOperation.setJmcFormula(op1, op2, InstructionType.IFF);
        symbolicOperation.setIntegerVariableMap(
                unionIntegerVariableMap(op1.getIntegerVariableMap(), op2.getIntegerVariableMap()));
        symbolicOperation.setBooleanVariableMap(
                unionBooleanVariableMap(op1.getBooleanVariableMap(), op2.getBooleanVariableMap()));
        return symbolicOperation;
    }

    public SymbolicOperation iff(SymbolicBoolean var1, SymbolicBoolean var2) {
        if (var1.getEval() != null && var2.getEval() != null) {
            SymbolicBoolean symbol1 = (SymbolicBoolean) var1.read();
            SymbolicBoolean symbol2 = (SymbolicBoolean) var2.read();
            return iff(symbol1.getEval(), symbol2.getEval());
        } else if (var1.getEval() != null) {
            SymbolicBoolean symbol1 = (SymbolicBoolean) var1.read();
            return iff(symbol1.getEval(), var2);
        } else if (var2.getEval() != null) {
            SymbolicBoolean symbol2 = (SymbolicBoolean) var2.read();
            return iff(var1, symbol2.getEval());
        } else {
            booleanVariableMap.clear();
            SymbolicOperation symbolicOperation = new SymbolicOperation();
            SymbolicBoolean symbol1 = (SymbolicBoolean) var1.read();
            SymbolicBoolean symbol2 = (SymbolicBoolean) var2.read();
            BooleanFormula leftOperand = makeBooleanFormula(symbol1);
            BooleanFormula rightOperand = makeBooleanFormula(symbol2);
            BooleanFormula formula = bmgr.equivalence(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setJmcFormula(symbol1, symbol2, InstructionType.IFF);
            symbolicOperation.setBooleanVariableMap(booleanVariableMap);
            return symbolicOperation;
        }
    }

    public SymbolicOperation iff(SymbolicOperation op, SymbolicBoolean var) {
        // SymbolicBoolean symbol = (SymbolicBoolean) var.read();
        // SymbolicOperation op2 = makeSymbolicOperation(symbol);
        SymbolicOperation op2 = atomicLiteral(var);
        return iff(op, op2);
    }

    public SymbolicOperation iff(SymbolicBoolean var, SymbolicOperation op) {
        return iff(op, var);
    }

    public SymbolicOperation xor(SymbolicOperation op1, SymbolicOperation op2) {
        SymbolicOperation symbolicOperation = new SymbolicOperation();
        BooleanFormula formula = bmgr.xor(op1.getFormula(), op2.getFormula());
        symbolicOperation.setFormula(formula);
        symbolicOperation.setJmcFormula(op1, op2, InstructionType.XOR);
        symbolicOperation.setIntegerVariableMap(
                unionIntegerVariableMap(op1.getIntegerVariableMap(), op2.getIntegerVariableMap()));
        symbolicOperation.setBooleanVariableMap(
                unionBooleanVariableMap(op1.getBooleanVariableMap(), op2.getBooleanVariableMap()));
        return symbolicOperation;
    }

    public SymbolicOperation xor(SymbolicBoolean var1, SymbolicBoolean var2) {
        if (var1.getEval() != null && var2.getEval() != null) {
            SymbolicBoolean symbol1 = (SymbolicBoolean) var1.read();
            SymbolicBoolean symbol2 = (SymbolicBoolean) var2.read();
            return xor(symbol1.getEval(), symbol2.getEval());
        } else if (var1.getEval() != null) {
            SymbolicBoolean symbol1 = (SymbolicBoolean) var1.read();
            return xor(symbol1.getEval(), var2);
        } else if (var2.getEval() != null) {
            SymbolicBoolean symbol2 = (SymbolicBoolean) var2.read();
            return xor(var1, symbol2.getEval());
        } else {
            booleanVariableMap.clear();
            SymbolicOperation symbolicOperation = new SymbolicOperation();
            SymbolicBoolean symbol1 = (SymbolicBoolean) var1.read();
            SymbolicBoolean symbol2 = (SymbolicBoolean) var2.read();
            BooleanFormula leftOperand = makeBooleanFormula(symbol1);
            BooleanFormula rightOperand = makeBooleanFormula(symbol2);
            BooleanFormula formula = bmgr.xor(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setJmcFormula(symbol1, symbol2, InstructionType.XOR);
            symbolicOperation.setBooleanVariableMap(booleanVariableMap);
            return symbolicOperation;
        }
    }

    public SymbolicOperation xor(SymbolicOperation op, SymbolicBoolean var) {
        // SymbolicBoolean symbol = (SymbolicBoolean) var.read();
        // SymbolicOperation op2 = makeSymbolicOperation(symbol);
        SymbolicOperation op2 = atomicLiteral(var);
        return xor(op, op2);
    }

    public SymbolicOperation xor(SymbolicBoolean var, SymbolicOperation op) {
        return xor(op, var);
    }

    private Map<String, IntegerFormula> unionIntegerVariableMap(
            Map<String, IntegerFormula> map1, Map<String, IntegerFormula> map2) {
        Map<String, IntegerFormula> unionMap = new HashMap<>();
        unionMap.putAll(map1);
        unionMap.putAll(map2);
        return unionMap;
    }

    public Map<String, BooleanFormula> unionBooleanVariableMap(
            Map<String, BooleanFormula> map1, Map<String, BooleanFormula> map2) {
        Map<String, BooleanFormula> unionMap = new HashMap<>();
        unionMap.putAll(map1);
        unionMap.putAll(map2);
        return unionMap;
    }

    private BooleanFormula makeBooleanFormula(SymbolicBoolean symbolicBoolean) {
        return findVariable(symbolicBoolean.getName());
    }

    private BooleanFormula findVariable(String name) {
        if (booleanVariableMap.containsKey(name)) {
            return booleanVariableMap.get(name);
        } else {
            SymBoolVariable symBoolVariable = solver.getSymBoolVariable(name);
            booleanVariableMap.put(name, symBoolVariable.getVar());
            return symBoolVariable.getVar();
        }
    }

    private SymbolicOperation makeSymbolicOperation(SymbolicBoolean symbolicBoolean) {
        if (symbolicBoolean.getEval() != null) {
            return symbolicBoolean.getEval();
        } else {
            booleanVariableMap.clear();
            SymbolicOperation symbolicOperation = new SymbolicOperation();
            BooleanFormula formula = findVariable(symbolicBoolean.getName());
            symbolicOperation.setFormula(formula);
            symbolicOperation.setBooleanVariableMap(booleanVariableMap);
            return symbolicOperation;
        }
    }
}
