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

public class ArithmeticFormula {

    private final Configuration config;
    private final LogManager logger;
    private final ShutdownManager shutdown;
    private final Map<String, IntegerFormula> integerVariableMap = new HashMap<>();
    private final SolverContext context;
    private final FormulaManager fmgr;
    private final BooleanFormulaManager bmgr;
    private final IntegerFormulaManager imgr;
    private final SymbolicSolver solver = RuntimeEnvironment.solver;

    public ArithmeticFormula() {
        config = solver.getConfig();
        logger = solver.getLogger();
        shutdown = solver.getShutdown();
        context = solver.getContext();
        fmgr = solver.getFmgr();
        bmgr = solver.getBmgr();
        imgr = solver.getImgr();
    }

    public SymbolicOperation eq(AbstractInteger var1, AbstractInteger var2) {
        integerVariableMap.clear();
        SymbolicOperation symbolicOperation = new SymbolicOperation();
        if (var1 instanceof SymbolicInteger && var2 instanceof SymbolicInteger) {
            IntegerFormula leftOperand = makeIntegerFormula((SymbolicInteger) var1.read());
            IntegerFormula rightOperand = makeIntegerFormula((SymbolicInteger) var2.read());
            BooleanFormula formula = imgr.equal(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setIntegerVariableMap(integerVariableMap);
        } else if (var1 instanceof SymbolicInteger && var2 instanceof ConcreteInteger) {
            IntegerFormula leftOperand = makeIntegerFormula((SymbolicInteger) var1.read());
            IntegerFormula rightOperand = imgr.makeNumber(var2.getValue());
            BooleanFormula formula = imgr.equal(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setIntegerVariableMap(integerVariableMap);
        } else if (var1 instanceof ConcreteInteger && var2 instanceof SymbolicInteger) {
            IntegerFormula leftOperand = imgr.makeNumber(var1.getValue());
            IntegerFormula rightOperand = makeIntegerFormula((SymbolicInteger) var2.read());
            BooleanFormula formula = imgr.equal(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setIntegerVariableMap(integerVariableMap);
        } else if (var1 instanceof ConcreteInteger && var2 instanceof ConcreteInteger) {
            IntegerFormula leftOperand = imgr.makeNumber(var1.getValue());
            IntegerFormula rightOperand = imgr.makeNumber(var2.getValue());
            BooleanFormula formula = imgr.equal(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setIntegerVariableMap(integerVariableMap);
        } else {
            System.out.println("[Symbolic Execution] Unsupported type");
            System.exit(0);
        }
        return symbolicOperation;
    }

    public SymbolicOperation eq(AbstractInteger var1, int var2) {
        ConcreteInteger concreteInteger = new ConcreteInteger(var2);
        return eq(var1, concreteInteger);
    }

    public SymbolicOperation eq(int var1, AbstractInteger var2) {
        return eq(var2, var1);
    }

    public SymbolicOperation neq(AbstractInteger var1, AbstractInteger var2) {
        integerVariableMap.clear();
        SymbolicOperation symbolicOperation = new SymbolicOperation();
        if (var1 instanceof SymbolicInteger && var2 instanceof SymbolicInteger) {
            IntegerFormula leftOperand = makeIntegerFormula((SymbolicInteger) var1.read());
            IntegerFormula rightOperand = makeIntegerFormula((SymbolicInteger) var2.read());
            BooleanFormula formula = bmgr.not(imgr.equal(leftOperand, rightOperand));
            symbolicOperation.setFormula(formula);
            symbolicOperation.setIntegerVariableMap(integerVariableMap);
        } else if (var1 instanceof SymbolicInteger && var2 instanceof ConcreteInteger) {
            IntegerFormula leftOperand = makeIntegerFormula((SymbolicInteger) var1.read());
            IntegerFormula rightOperand = imgr.makeNumber(var2.getValue());
            BooleanFormula formula = bmgr.not(imgr.equal(leftOperand, rightOperand));
            symbolicOperation.setFormula(formula);
            symbolicOperation.setIntegerVariableMap(integerVariableMap);
        } else if (var1 instanceof ConcreteInteger && var2 instanceof SymbolicInteger) {
            IntegerFormula leftOperand = imgr.makeNumber(var1.getValue());
            IntegerFormula rightOperand = makeIntegerFormula((SymbolicInteger) var2.read());
            BooleanFormula formula = bmgr.not(imgr.equal(leftOperand, rightOperand));
            symbolicOperation.setFormula(formula);
            symbolicOperation.setIntegerVariableMap(integerVariableMap);
        } else if (var1 instanceof ConcreteInteger && var2 instanceof ConcreteInteger) {
            IntegerFormula leftOperand = imgr.makeNumber(var1.getValue());
            IntegerFormula rightOperand = imgr.makeNumber(var2.getValue());
            BooleanFormula formula = bmgr.not(imgr.equal(leftOperand, rightOperand));
            symbolicOperation.setFormula(formula);
            symbolicOperation.setIntegerVariableMap(integerVariableMap);
        } else {
            System.out.println("[Symbolic Execution] Unsupported type");
            System.exit(0);
        }
        return symbolicOperation;
    }

    public SymbolicOperation neq(AbstractInteger var1, int var2) {
        ConcreteInteger concreteInteger = new ConcreteInteger(var2);
        return neq(var1, concreteInteger);
    }

    public SymbolicOperation neq(int var1, AbstractInteger var2) {
        return neq(var2, var1);
    }

    public SymbolicOperation geq(AbstractInteger var1, AbstractInteger var2) {
        integerVariableMap.clear();
        SymbolicOperation symbolicOperation = new SymbolicOperation();
        if (var1 instanceof SymbolicInteger && var2 instanceof SymbolicInteger) {
            IntegerFormula leftOperand = makeIntegerFormula((SymbolicInteger) var1.read());
            IntegerFormula rightOperand = makeIntegerFormula((SymbolicInteger) var2.read());
            BooleanFormula formula = imgr.greaterOrEquals(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setIntegerVariableMap(integerVariableMap);
        } else if (var1 instanceof SymbolicInteger && var2 instanceof ConcreteInteger) {
            IntegerFormula leftOperand = makeIntegerFormula((SymbolicInteger) var1.read());
            IntegerFormula rightOperand = imgr.makeNumber(var2.getValue());
            BooleanFormula formula = imgr.greaterOrEquals(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setIntegerVariableMap(integerVariableMap);
        } else if (var1 instanceof ConcreteInteger && var2 instanceof SymbolicInteger) {
            IntegerFormula leftOperand = imgr.makeNumber(var1.getValue());
            IntegerFormula rightOperand = makeIntegerFormula((SymbolicInteger) var2.read());
            BooleanFormula formula = imgr.greaterOrEquals(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setIntegerVariableMap(integerVariableMap);
        } else if (var1 instanceof ConcreteInteger && var2 instanceof ConcreteInteger) {
            IntegerFormula leftOperand = imgr.makeNumber(var1.getValue());
            IntegerFormula rightOperand = imgr.makeNumber(var2.getValue());
            BooleanFormula formula = imgr.greaterOrEquals(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setIntegerVariableMap(integerVariableMap);
        } else {
            System.out.println("[Symbolic Execution] Unsupported type");
            System.exit(0);
        }
        return symbolicOperation;
    }

    public SymbolicOperation geq(AbstractInteger var1, int var2) {
        ConcreteInteger concreteInteger = new ConcreteInteger(var2);
        return geq(var1, concreteInteger);
    }

    public SymbolicOperation geq(int var1, AbstractInteger var2) {
        return geq(var2, var1);
    }

    public SymbolicOperation leq(AbstractInteger var1, AbstractInteger var2) {
        integerVariableMap.clear();
        SymbolicOperation symbolicOperation = new SymbolicOperation();
        if (var1 instanceof SymbolicInteger && var2 instanceof SymbolicInteger) {
            IntegerFormula leftOperand = makeIntegerFormula((SymbolicInteger) var1.read());
            IntegerFormula rightOperand = makeIntegerFormula((SymbolicInteger) var2.read());
            BooleanFormula formula = imgr.lessOrEquals(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setIntegerVariableMap(integerVariableMap);
        } else if (var1 instanceof SymbolicInteger && var2 instanceof ConcreteInteger) {
            IntegerFormula leftOperand = makeIntegerFormula((SymbolicInteger) var1.read());
            IntegerFormula rightOperand = imgr.makeNumber(var2.getValue());
            BooleanFormula formula = imgr.lessOrEquals(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setIntegerVariableMap(integerVariableMap);
        } else if (var1 instanceof ConcreteInteger && var2 instanceof SymbolicInteger) {
            IntegerFormula leftOperand = imgr.makeNumber(var1.getValue());
            IntegerFormula rightOperand = makeIntegerFormula((SymbolicInteger) var2.read());
            BooleanFormula formula = imgr.lessOrEquals(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setIntegerVariableMap(integerVariableMap);
        } else if (var1 instanceof ConcreteInteger && var2 instanceof ConcreteInteger) {
            IntegerFormula leftOperand = imgr.makeNumber(var1.getValue());
            IntegerFormula rightOperand = imgr.makeNumber(var2.getValue());
            BooleanFormula formula = imgr.lessOrEquals(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setIntegerVariableMap(integerVariableMap);
        } else {
            System.out.println("[Symbolic Execution] Unsupported type");
            System.exit(0);
        }
        return symbolicOperation;
    }

    public SymbolicOperation leq(AbstractInteger var1, int var2) {
        ConcreteInteger concreteInteger = new ConcreteInteger(var2);
        return leq(var1, concreteInteger);
    }

    public SymbolicOperation leq(int var1, AbstractInteger var2) {
        return leq(var2, var1);
    }

    public SymbolicOperation gt(AbstractInteger var1, AbstractInteger var2) {
        integerVariableMap.clear();
        SymbolicOperation symbolicOperation = new SymbolicOperation();
        if (var1 instanceof SymbolicInteger && var2 instanceof SymbolicInteger) {
            IntegerFormula leftOperand = makeIntegerFormula((SymbolicInteger) var1.read());
            IntegerFormula rightOperand = makeIntegerFormula((SymbolicInteger) var2.read());
            BooleanFormula formula = imgr.greaterThan(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setIntegerVariableMap(integerVariableMap);
        } else if (var1 instanceof SymbolicInteger && var2 instanceof ConcreteInteger) {
            IntegerFormula leftOperand = makeIntegerFormula((SymbolicInteger) var1.read());
            IntegerFormula rightOperand = imgr.makeNumber(var2.getValue());
            BooleanFormula formula = imgr.greaterThan(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setIntegerVariableMap(integerVariableMap);
        } else if (var1 instanceof ConcreteInteger && var2 instanceof SymbolicInteger) {
            IntegerFormula leftOperand = imgr.makeNumber(var1.getValue());
            IntegerFormula rightOperand = makeIntegerFormula((SymbolicInteger) var2.read());
            BooleanFormula formula = imgr.greaterThan(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setIntegerVariableMap(integerVariableMap);
        } else if (var1 instanceof ConcreteInteger && var2 instanceof ConcreteInteger) {
            IntegerFormula leftOperand = imgr.makeNumber(var1.getValue());
            IntegerFormula rightOperand = imgr.makeNumber(var2.getValue());
            BooleanFormula formula = imgr.greaterThan(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setIntegerVariableMap(integerVariableMap);
        } else {
            System.out.println("[Symbolic Execution] Unsupported type");
            System.exit(0);
        }
        return symbolicOperation;
    }

    public SymbolicOperation gt(AbstractInteger var1, int var2) {
        ConcreteInteger concreteInteger = new ConcreteInteger(var2);
        return gt(var1, concreteInteger);
    }

    public SymbolicOperation gt(int var1, AbstractInteger var2) {
        return gt(var2, var1);
    }

    public SymbolicOperation lt(AbstractInteger var1, AbstractInteger var2) {
        integerVariableMap.clear();
        SymbolicOperation symbolicOperation = new SymbolicOperation();
        if (var1 instanceof SymbolicInteger && var2 instanceof SymbolicInteger) {
            IntegerFormula leftOperand = makeIntegerFormula((SymbolicInteger) var1.read());
            IntegerFormula rightOperand = makeIntegerFormula((SymbolicInteger) var2.read());
            BooleanFormula formula = imgr.lessThan(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setIntegerVariableMap(integerVariableMap);
        } else if (var1 instanceof SymbolicInteger && var2 instanceof ConcreteInteger) {
            IntegerFormula leftOperand = makeIntegerFormula((SymbolicInteger) var1.read());
            IntegerFormula rightOperand = imgr.makeNumber(var2.getValue());
            BooleanFormula formula = imgr.lessThan(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setIntegerVariableMap(integerVariableMap);
        } else if (var1 instanceof ConcreteInteger && var2 instanceof SymbolicInteger) {
            IntegerFormula leftOperand = imgr.makeNumber(var1.getValue());
            IntegerFormula rightOperand = makeIntegerFormula((SymbolicInteger) var2.read());
            BooleanFormula formula = imgr.lessThan(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setIntegerVariableMap(integerVariableMap);
        } else if (var1 instanceof ConcreteInteger && var2 instanceof ConcreteInteger) {
            IntegerFormula leftOperand = imgr.makeNumber(var1.getValue());
            IntegerFormula rightOperand = imgr.makeNumber(var2.getValue());
            BooleanFormula formula = imgr.lessThan(leftOperand, rightOperand);
            symbolicOperation.setFormula(formula);
            symbolicOperation.setIntegerVariableMap(integerVariableMap);
        } else {
            System.out.println("[Symbolic Execution] Unsupported type");
            System.exit(0);
        }
        return symbolicOperation;
    }

    public SymbolicOperation lt(AbstractInteger var1, int var2) {
        ConcreteInteger concreteInteger = new ConcreteInteger(var2);
        return lt(var1, concreteInteger);
    }

    public SymbolicOperation lt(int var1, AbstractInteger var2) {
        return lt(var2, var1);
    }

    private IntegerFormula makeIntegerFormula(SymbolicInteger symbolicInteger) {
        if (symbolicInteger.getEval() == null) {
            return findVariable(symbolicInteger.getName());
        }
        IntegerFormula leftOperand = symbolicInteger.getEval().getLeft() instanceof SymbolicInteger
                ? makeIntegerFormula((SymbolicInteger) symbolicInteger.getEval().getLeft())
                : imgr.makeNumber(symbolicInteger.getEval().getLeft().getValue());
        IntegerFormula rightOperand = symbolicInteger.getEval().getRight() instanceof SymbolicInteger
                ? makeIntegerFormula((SymbolicInteger) symbolicInteger.getEval().getRight())
                : imgr.makeNumber(symbolicInteger.getEval().getRight().getValue());

        switch (symbolicInteger.getEval().getOperator()) {
            case ADD:
                return imgr.add(leftOperand, rightOperand);
            case SUB:
                return imgr.subtract(leftOperand, rightOperand);
            case MUL:
                return imgr.multiply(leftOperand, rightOperand);
            case DIV:
                return imgr.divide(leftOperand, rightOperand);
            default:
                System.out.println("[Symbolic Execution] Unsupported operator [" + symbolicInteger.getEval().getOperator() + "]");
                System.exit(0);
                return null;
        }
    }

    private IntegerFormula findVariable(String name) {
        if (integerVariableMap.containsKey(name)) {
            return integerVariableMap.get(name);
        } else {
            SymIntVariable symIntVariable = solver.getSymIntVariable(name);
            integerVariableMap.put(name, symIntVariable.getVar());
            return symIntVariable.getVar();
        }
    }

    public Map<String, IntegerFormula> getIntegerVariableMap() {
        return integerVariableMap;
    }
}