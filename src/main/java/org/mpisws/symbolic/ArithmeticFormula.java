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

    /**
     * @property {@link #config} is used to store the configuration of the symbolic solver.
     */
    private final Configuration config;

    /**
     * @property {@link #logger} is used to store the logger of the symbolic solver.
     */
    private final LogManager logger;

    /**
     * @property {@link #shutdown} is used to store the shutdown manager of the symbolic solver.
     */
    private final ShutdownManager shutdown;

    /**
     * @property {@link #integerVariableMap} is used to store the symbolic integer variable map.
     */
    private final Map<String, IntegerFormula> integerVariableMap = new HashMap<>();

    /**
     * @property {@link #context} is used to store the solver context of the symbolic solver.
     */
    private final SolverContext context;

    /**
     * @property {@link #fmgr} is used to store the formula manager of the symbolic solver.
     */
    private final FormulaManager fmgr;

    /**
     * @property {@link #bmgr} is used to store the boolean formula manager of the symbolic solver.
     */
    private final BooleanFormulaManager bmgr;

    /**
     * @property {@link #imgr} is used to store the integer formula manager of the symbolic solver.
     */
    private final IntegerFormulaManager imgr;

    /**
     * @property {@link #solver} is used to store the symbolic solver of the {@link RuntimeEnvironment}
     */
    private final SymbolicSolver solver = RuntimeEnvironment.solver;

    /**
     * Initializes a newly created {@link ArithmeticFormula} object.
     */
    public ArithmeticFormula() {
        config = solver.getConfig();
        logger = solver.getLogger();
        shutdown = solver.getShutdown();
        context = solver.getContext();
        fmgr = solver.getFmgr();
        bmgr = solver.getBmgr();
        imgr = solver.getImgr();
    }

    /**
     * Creates a symbolic equality operation based on two abstract integers.
     *
     * <p>
     * The method creates a symbolic equality operation based on two abstract integers. First, it clears the integer
     * variable map which stores the symbolic integer variable which are used in the operation. Then, it creates a
     * symbolic operation object and checks the type of the abstract integers. If both abstract integers are symbolic
     * integers, it creates an integer formula for the left and right operands and creates a boolean formula for the
     * equality operation. If one of the abstract integers is a symbolic integer and the other is a concrete integer, it
     * creates an integer formula for the left operand and a number formula for the right operand and creates a boolean
     * formula for the equality operation. If both abstract integers are concrete integers, it creates a number formula
     * for the left and right operands and creates a boolean formula for the equality operation. If the type of the
     * abstract integers is not supported, it prints an error message and exits the program.
     * </p>
     *
     * @param var1 the first abstract integer.
     * @param var2 the second abstract integer.
     * @return the symbolic equality operation.
     */
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
        symbolicOperation.setJmcFormula(var1, var2, InstructionType.EQ);
        return symbolicOperation;
    }

    /**
     * Creates a symbolic equality operation based on an abstract integer and an integer value.
     * <p>
     * This method creates a symbolic equality operation based on an abstract integer and an integer value. It creates a
     * concrete integer object with the integer value and calls the {@link #eq(AbstractInteger, AbstractInteger)} method
     * with the abstract integer and the concrete integer.
     * </p>
     *
     * @param var1 the abstract integer.
     * @param var2 the integer value.
     * @return
     */
    public SymbolicOperation eq(AbstractInteger var1, int var2) {
        ConcreteInteger concreteInteger = new ConcreteInteger(var2);
        return eq(var1, concreteInteger);
    }

    /**
     * Creates a symbolic equality operation based on an integer value and an abstract integer.
     * <p>
     * This method creates a symbolic equality operation based on an integer value and an abstract integer by calling the
     * {@link #eq(AbstractInteger, int)} method with the abstract integer and the integer value.
     * </p>
     *
     * @param var1 the integer value.
     * @param var2 the abstract integer.
     * @return the symbolic equality operation.
     */
    public SymbolicOperation eq(int var1, AbstractInteger var2) {
        return eq(var2, var1);
    }

    /**
     * Creates a symbolic inequality operation based on two abstract integers.
     * <p>
     * This method creates a symbolic inequality operation based on two abstract integers. First, it clears the integer
     * variable map which stores the symbolic integer variable which are used in the operation. Then, it creates a
     * symbolic operation object and checks the type of the abstract integers. If both abstract integers are symbolic
     * integers, it creates an integer formula for the left and right operands and creates a boolean formula for the
     * inequality operation. If one of the abstract integers is a symbolic integer and the other is a concrete integer,
     * it creates an integer formula for the left operand and a number formula for the right operand and creates a
     * boolean formula for the inequality operation. If both abstract integers are concrete integers, it creates a number
     * formula for the left and right operands and creates a boolean formula for the inequality operation. If the type of
     * the abstract integers is not supported, it prints an error message and exits the program.
     * </p>
     *
     * @param var1 the first abstract integer.
     * @param var2 the second abstract integer.
     * @return the symbolic inequality operation.
     */
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
        symbolicOperation.setJmcFormula(var1, var2, InstructionType.NEQ);
        return symbolicOperation;
    }

    /**
     * Creates a symbolic inequality operation based on an abstract integer and an integer value.
     * <p>
     * This method creates a symbolic inequality operation based on an abstract integer and an integer value. It creates
     * a concrete integer object with the integer value and calls the {@link #neq(AbstractInteger, AbstractInteger)} method
     * with the abstract integer and the concrete integer.
     * </p>
     *
     * @param var1 the abstract integer.
     * @param var2 the integer value.
     * @return the symbolic inequality operation.
     */
    public SymbolicOperation neq(AbstractInteger var1, int var2) {
        ConcreteInteger concreteInteger = new ConcreteInteger(var2);
        return neq(var1, concreteInteger);
    }

    /**
     * Creates a symbolic inequality operation based on an integer value and an abstract integer.
     * <p>
     * This method creates a symbolic inequality operation based on an integer value and an abstract integer by calling
     * the {@link #neq(AbstractInteger, int)} method with the abstract integer and the integer value.
     * </p>
     *
     * @param var1 the integer value.
     * @param var2 the abstract integer.
     * @return the symbolic inequality operation.
     */
    public SymbolicOperation neq(int var1, AbstractInteger var2) {
        return neq(var2, var1);
    }

    /**
     * Creates a symbolic greater than or equal (geq) operation based on two abstract integers.
     * <p>
     * This method creates a symbolic geq operation based on two abstract integers. First, it clears the
     * integer variable map which stores the symbolic integer variable which are used in the operation. Then, it creates a
     * symbolic operation object and checks the type of the abstract integers. If both abstract integers are symbolic
     * integers, it creates an integer formula for the left and right operands and creates a boolean formula for the
     * geq operation. If one of the abstract integers is a symbolic integer and the other is a concrete
     * integer, it creates an integer formula for the left operand and a number formula for the right operand and creates
     * a boolean formula for the geq operation. If both abstract integers are concrete integers, it
     * creates a number formula for the left and right operands and creates a boolean formula for the geq operation.
     * If the type of the abstract integers is not supported, it prints an error message and exits the program.
     * </p>
     *
     * @param var1 the first abstract integer.
     * @param var2 the second abstract integer.
     * @return the symbolic greater than or equal operation.
     */
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
        symbolicOperation.setJmcFormula(var1, var2, InstructionType.GEQ);
        return symbolicOperation;
    }

    /**
     * Creates a symbolic greater than or equal (geq) operation based on an abstract integer and an integer value.
     * <p>
     * This method creates a symbolic geq operation based on an abstract integer and an integer value. It creates a
     * concrete integer object with the integer value and calls the {@link #geq(AbstractInteger, AbstractInteger)} method
     * with the abstract integer and the concrete integer.
     * </p>
     *
     * @param var1 the abstract integer.
     * @param var2 the integer value.
     * @return the symbolic greater than or equal operation.
     */
    public SymbolicOperation geq(AbstractInteger var1, int var2) {
        ConcreteInteger concreteInteger = new ConcreteInteger(var2);
        return geq(var1, concreteInteger);
    }

    /**
     * Creates a symbolic greater than or equal (geq) operation based on an integer value and an abstract integer.
     * <p>
     * This method creates a symbolic geq operation based on an integer value and an abstract integer by calling the
     * {@link #geq(AbstractInteger, int)} method with the abstract integer and the integer value.
     * </p>
     *
     * @param var1 the integer value.
     * @param var2 the abstract integer.
     * @return the symbolic greater than or equal operation.
     */
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
        symbolicOperation.setJmcFormula(var1, var2, InstructionType.LEQ);
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
        symbolicOperation.setJmcFormula(var1, var2, InstructionType.GT);
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
        symbolicOperation.setJmcFormula(var1, var2, InstructionType.LT);
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
            case MOD:
                return imgr.modulo(leftOperand, rightOperand);
            default:
                System.out.println("[Symbolic Execution] Unsupported operator [" +
                        symbolicInteger.getEval().getOperator() + "]");
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