package org.mpisws.symbolic;

import org.mpisws.runtime.RuntimeEnvironment;
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
    private final SymbolicSolver solver = RuntimeEnvironment.solver;

    public PropositionalFormula() {
        config = RuntimeEnvironment.solver.getConfig();
        logger = RuntimeEnvironment.solver.getLogger();
        shutdown = RuntimeEnvironment.solver.getShutdown();
        context = RuntimeEnvironment.solver.getContext();
        fmgr = RuntimeEnvironment.solver.getFmgr();
        bmgr = RuntimeEnvironment.solver.getBmgr();
        imgr = RuntimeEnvironment.solver.getImgr();
    }

    public SymbolicOperation and(SymbolicOperation op1, SymbolicOperation op2) {
        SymbolicOperation symbolicOperation = new SymbolicOperation();
        BooleanFormula formula = bmgr.and(op1.getFormula(), op2.getFormula());
        symbolicOperation.setFormula(formula);
        symbolicOperation.setIntegerVariableMap(unionIntegerVariableMap(op1.getIntegerVariableMap(), op2.getIntegerVariableMap()));
        symbolicOperation.setBooleanVariableMap(unionBooleanVariableMap(op1.getBooleanVariableMap(), op2.getBooleanVariableMap()));
        return symbolicOperation;
    }

    public SymbolicOperation and(SymbolicBoolean var1, SymbolicBoolean var2) {
        if (var1.getEval() != null && var2.getEval() != null) {
            return and(var1.getEval(), var2.getEval());
        } else if (var1.getEval() != null) {
            return and(var1.getEval(), var2);
        } else if (var2.getEval() != null) {
            return and(var1, var2.getEval());
        } else {
            booleanVariableMap.clear();
            SymbolicOperation symbolicOperation = new SymbolicOperation();
            BooleanFormula leftOperand = makeBooleanFormula(var1);
            BooleanFormula rightOperand = makeBooleanFormula(var2);
            BooleanFormula formula = bmgr.and(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setBooleanVariableMap(booleanVariableMap);
            return symbolicOperation;
        }
    }

    public SymbolicOperation and(SymbolicOperation op1, SymbolicBoolean var) {
        SymbolicOperation op2 = makeSymbolicOperation(var);
        return and(op1, op2);
    }

    public SymbolicOperation and(SymbolicBoolean var, SymbolicOperation op) {
        return and(op, var);
    }

    public SymbolicOperation or(SymbolicOperation op1, SymbolicOperation op2) {
        SymbolicOperation symbolicOperation = new SymbolicOperation();
        BooleanFormula formula = bmgr.or(op1.getFormula(), op2.getFormula());
        symbolicOperation.setFormula(formula);
        symbolicOperation.setIntegerVariableMap(unionIntegerVariableMap(op1.getIntegerVariableMap(), op2.getIntegerVariableMap()));
        symbolicOperation.setBooleanVariableMap(unionBooleanVariableMap(op1.getBooleanVariableMap(), op2.getBooleanVariableMap()));
        return symbolicOperation;
    }

    public SymbolicOperation or(SymbolicBoolean var1, SymbolicBoolean var2) {
        if (var1.getEval() != null && var2.getEval() != null) {
            return or(var1.getEval(), var2.getEval());
        } else if (var1.getEval() != null) {
            return or(var1.getEval(), var2);
        } else if (var2.getEval() != null) {
            return or(var1, var2.getEval());
        } else {
            booleanVariableMap.clear();
            SymbolicOperation symbolicOperation = new SymbolicOperation();
            BooleanFormula leftOperand = makeBooleanFormula(var1);
            BooleanFormula rightOperand = makeBooleanFormula(var2);
            BooleanFormula formula = bmgr.or(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setBooleanVariableMap(booleanVariableMap);
            return symbolicOperation;
        }
    }

    public SymbolicOperation or(SymbolicOperation op1, SymbolicBoolean var) {
        SymbolicOperation op2 = makeSymbolicOperation(var);
        return or(op1, op2);
    }

    public SymbolicOperation or(SymbolicBoolean var, SymbolicOperation op) {
        return or(op, var);
    }

    public SymbolicOperation not(SymbolicOperation op) {
        SymbolicOperation symbolicOperation = new SymbolicOperation();
        BooleanFormula formula = bmgr.not(op.getFormula());
        symbolicOperation.setFormula(formula);
        symbolicOperation.setIntegerVariableMap(op.getIntegerVariableMap());
        symbolicOperation.setBooleanVariableMap(op.getBooleanVariableMap());
        return symbolicOperation;
    }

    public SymbolicOperation not(SymbolicBoolean var) {
        if (var.getEval() != null) {
            return not(var.getEval());
        } else {
            booleanVariableMap.clear();
            SymbolicOperation symbolicOperation = new SymbolicOperation();
            BooleanFormula formula = bmgr.not(makeBooleanFormula(var));
            symbolicOperation.setFormula(formula);
            symbolicOperation.setBooleanVariableMap(booleanVariableMap);
            return symbolicOperation;
        }
    }

    public SymbolicOperation implies(SymbolicOperation op1, SymbolicOperation op2) {
        SymbolicOperation symbolicOperation = new SymbolicOperation();
        BooleanFormula formula = bmgr.implication(op1.getFormula(), op2.getFormula());
        symbolicOperation.setFormula(formula);
        symbolicOperation.setIntegerVariableMap(unionIntegerVariableMap(op1.getIntegerVariableMap(), op2.getIntegerVariableMap()));
        symbolicOperation.setBooleanVariableMap(unionBooleanVariableMap(op1.getBooleanVariableMap(), op2.getBooleanVariableMap()));
        return symbolicOperation;
    }

    public SymbolicOperation implies(SymbolicBoolean var1, SymbolicBoolean var2) {
        if (var1.getEval() != null && var2.getEval() != null) {
            return implies(var1.getEval(), var2.getEval());
        } else if (var1.getEval() != null) {
            return implies(var1.getEval(), var2);
        } else if (var2.getEval() != null) {
            return implies(var1, var2.getEval());
        } else {
            booleanVariableMap.clear();
            SymbolicOperation symbolicOperation = new SymbolicOperation();
            BooleanFormula leftOperand = makeBooleanFormula(var1);
            BooleanFormula rightOperand = makeBooleanFormula(var2);
            BooleanFormula formula = bmgr.implication(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setBooleanVariableMap(booleanVariableMap);
            return symbolicOperation;
        }
    }

    public SymbolicOperation implies(SymbolicOperation op, SymbolicBoolean var) {
        SymbolicOperation op2 = makeSymbolicOperation(var);
        return implies(op, op2);
    }

    public SymbolicOperation implies(SymbolicBoolean var, SymbolicOperation op) {
        return implies(op, var);
    }

    public SymbolicOperation iff(SymbolicOperation op1, SymbolicOperation op2) {
        SymbolicOperation symbolicOperation = new SymbolicOperation();
        BooleanFormula formula = bmgr.equivalence(op1.getFormula(), op2.getFormula());
        symbolicOperation.setFormula(formula);
        symbolicOperation.setIntegerVariableMap(unionIntegerVariableMap(op1.getIntegerVariableMap(), op2.getIntegerVariableMap()));
        symbolicOperation.setBooleanVariableMap(unionBooleanVariableMap(op1.getBooleanVariableMap(), op2.getBooleanVariableMap()));
        return symbolicOperation;
    }

    public SymbolicOperation iff(SymbolicBoolean var1, SymbolicBoolean var2) {
        if (var1.getEval() != null && var2.getEval() != null) {
            return iff(var1.getEval(), var2.getEval());
        } else if (var1.getEval() != null) {
            return iff(var1.getEval(), var2);
        } else if (var2.getEval() != null) {
            return iff(var1, var2.getEval());
        } else {
            booleanVariableMap.clear();
            SymbolicOperation symbolicOperation = new SymbolicOperation();
            BooleanFormula leftOperand = makeBooleanFormula(var1);
            BooleanFormula rightOperand = makeBooleanFormula(var2);
            BooleanFormula formula = bmgr.equivalence(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setBooleanVariableMap(booleanVariableMap);
            return symbolicOperation;
        }
    }

    public SymbolicOperation iff(SymbolicOperation op, SymbolicBoolean var) {
        SymbolicOperation op2 = makeSymbolicOperation(var);
        return iff(op, op2);
    }

    public SymbolicOperation iff(SymbolicBoolean var, SymbolicOperation op) {
        return iff(op, var);
    }

    public SymbolicOperation xor(SymbolicOperation op1, SymbolicOperation op2) {
        SymbolicOperation symbolicOperation = new SymbolicOperation();
        BooleanFormula formula = bmgr.xor(op1.getFormula(), op2.getFormula());
        symbolicOperation.setFormula(formula);
        symbolicOperation.setIntegerVariableMap(unionIntegerVariableMap(op1.getIntegerVariableMap(), op2.getIntegerVariableMap()));
        symbolicOperation.setBooleanVariableMap(unionBooleanVariableMap(op1.getBooleanVariableMap(), op2.getBooleanVariableMap()));
        return symbolicOperation;
    }

    public SymbolicOperation xor(SymbolicBoolean var1, SymbolicBoolean var2) {
        if (var1.getEval() != null && var2.getEval() != null) {
            return xor(var1.getEval(), var2.getEval());
        } else if (var1.getEval() != null) {
            return xor(var1.getEval(), var2);
        } else if (var2.getEval() != null) {
            return xor(var1, var2.getEval());
        } else {
            booleanVariableMap.clear();
            SymbolicOperation symbolicOperation = new SymbolicOperation();
            BooleanFormula leftOperand = makeBooleanFormula(var1);
            BooleanFormula rightOperand = makeBooleanFormula(var2);
            BooleanFormula formula = bmgr.xor(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setBooleanVariableMap(booleanVariableMap);
            return symbolicOperation;
        }
    }

    public SymbolicOperation xor(SymbolicOperation op, SymbolicBoolean var) {
        SymbolicOperation op2 = makeSymbolicOperation(var);
        return xor(op, op2);
    }

    public SymbolicOperation xor(SymbolicBoolean var, SymbolicOperation op) {
        return xor(op, var);
    }

    private Map<String, IntegerFormula> unionIntegerVariableMap(Map<String, IntegerFormula> map1, Map<String, IntegerFormula> map2) {
        Map<String, IntegerFormula> unionMap = new HashMap<>();
        unionMap.putAll(map1);
        unionMap.putAll(map2);
        return unionMap;
    }

    public Map<String, BooleanFormula> unionBooleanVariableMap(Map<String, BooleanFormula> map1, Map<String, BooleanFormula> map2) {
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