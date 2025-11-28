package org.mpi_sws.jmc.symbolic.integer;

import org.mpi_sws.jmc.symbolic.InstructionType;
import org.mpi_sws.jmc.symbolic.JmcSymbolic;
import org.mpi_sws.jmc.symbolic.SymbolicOperand;
import org.mpi_sws.jmc.symbolic.bool.JmcBooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.IntegerFormulaManager;
import org.sosy_lab.java_smt.api.NumeralFormula;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ArithmeticFormula {

    /**
     * @property {@link #integerVariableMap} is used to store the symbolic integer variable map.
     */
    private Map<String, NumeralFormula.IntegerFormula> integerVariableMap = new HashMap<>();

    /**
     * @property {@link #bmgr} is used to store the boolean formula manager of the symbolic solver.
     */
    private final BooleanFormulaManager bmgr;

    /**
     * @property {@link #imgr} is used to store the integer formula manager of the symbolic solver.
     */
    private final IntegerFormulaManager imgr;

    /**
     * Initializes a newly created {@link ArithmeticFormula} object.
     */
    public ArithmeticFormula() {
        bmgr = JmcSymbolic.getBmgr();
        imgr = JmcSymbolic.getImgr();
    }

    /**
     * Creates a symbolic equality operation based on two abstract integers.
     *
     * <p>The method creates a symbolic equality operation based on two abstract integers. First, it
     * clears the integer variable map which stores the symbolic integer variable which are used in
     * the operation. Then, it creates a symbolic operation object and checks the type of the
     * abstract integers. If both abstract integers are symbolic integers, it creates an integer
     * formula for the left and right operands and creates a boolean formula for the equality
     * operation. If one of the abstract integers is a symbolic integer and the other is a concrete
     * integer, it creates an integer formula for the left operand and a number formula for the
     * right operand and creates a boolean formula for the equality operation. If both abstract
     * integers are concrete integers, it creates a number formula for the left and right operands
     * and creates a boolean formula for the equality operation. If the type of the abstract
     * integers is not supported, it prints an error message and exits the program.
     *
     * @param var1 the first abstract integer.
     * @param var2 the second abstract integer.
     * @return the boolean equality formula.
     */
    public JmcBooleanFormula eq(AbstractInteger var1, AbstractInteger var2) {
        return makeBooleanFormula(var1, var2, InstructionType.EQ);
    }

    /**
     * Creates a symbolic equality operation based on an abstract integer and an integer value.
     *
     * <p>This method creates a symbolic equality operation based on an abstract integer and an
     * integer value. It creates a concrete integer object with the integer value and calls the
     * {@link #eq(AbstractInteger, AbstractInteger)} method with the abstract integer and the
     * concrete integer.
     *
     * @param var1 the abstract integer.
     * @param var2 the integer value.
     * @return the boolean equality formula.
     */
    public JmcBooleanFormula eq(AbstractInteger var1, int var2) {
        ConcreteInteger concreteInteger = new ConcreteInteger(var2);
        return eq(var1, concreteInteger);
    }

    /**
     * Creates a symbolic equality operation based on an integer value and an abstract integer.
     *
     * <p>This method creates a symbolic equality operation based on an integer value and an
     * abstract integer by calling the {@link #eq(AbstractInteger, int)} method with the abstract
     * integer and the integer value.
     *
     * @param var1 the integer value.
     * @param var2 the abstract integer.
     * @return the boolean equality formula.
     */
    public JmcBooleanFormula eq(int var1, AbstractInteger var2) {
        return eq(var2, var1);
    }

    /**
     * Creates a symbolic inequality operation based on two abstract integers.
     *
     * <p>This method creates a symbolic inequality operation based on two abstract integers. First,
     * it clears the integer variable map which stores the symbolic integer variable which are used
     * in the operation. Then, it creates a symbolic operation object and checks the type of the
     * abstract integers. If both abstract integers are symbolic integers, it creates an integer
     * formula for the left and right operands and creates a boolean formula for the inequality
     * operation. If one of the abstract integers is a symbolic integer and the other is a concrete
     * integer, it creates an integer formula for the left operand and a number formula for the
     * right operand and creates a boolean formula for the inequality operation. If both abstract
     * integers are concrete integers, it creates a number formula for the left and right operands
     * and creates a boolean formula for the inequality operation. If the type of the abstract
     * integers is not supported, it prints an error message and exits the program.
     *
     * @param var1 the first abstract integer.
     * @param var2 the second abstract integer.
     * @return the boolean inequality formula.
     */
    public JmcBooleanFormula neq(AbstractInteger var1, AbstractInteger var2) {
        return makeBooleanFormula(var1, var2, InstructionType.NEQ);
    }

    /**
     * Creates a symbolic inequality operation based on an abstract integer and an integer value.
     *
     * <p>This method creates a symbolic inequality operation based on an abstract integer and an
     * integer value. It creates a concrete integer object with the integer value and calls the
     * {@link #neq(AbstractInteger, AbstractInteger)} method with the abstract integer and the
     * concrete integer.
     *
     * @param var1 the abstract integer.
     * @param var2 the integer value.
     * @return the boolean inequality formula.
     */
    public JmcBooleanFormula neq(AbstractInteger var1, int var2) {
        ConcreteInteger concreteInteger = new ConcreteInteger(var2);
        return neq(var1, concreteInteger);
    }

    /**
     * Creates a symbolic inequality operation based on an integer value and an abstract integer.
     *
     * <p>This method creates a symbolic inequality operation based on an integer value and an
     * abstract integer by calling the {@link #neq(AbstractInteger, int)} method with the abstract
     * integer and the integer value.
     *
     * @param var1 the integer value.
     * @param var2 the abstract integer.
     * @return the boolean inequality formula.
     */
    public JmcBooleanFormula neq(int var1, AbstractInteger var2) {
        return neq(var2, var1);
    }

    /**
     * Creates a symbolic greater than or equal (geq) operation based on two abstract integers.
     *
     * <p>This method creates a symbolic geq operation based on two abstract integers. First, it
     * clears the integer variable map which stores the symbolic integer variable which are used in
     * the operation. Then, it creates a symbolic operation object and checks the type of the
     * abstract integers. If both abstract integers are symbolic integers, it creates an integer
     * formula for the left and right operands and creates a boolean formula for the geq operation.
     * If one of the abstract integers is a symbolic integer and the other is a concrete integer, it
     * creates an integer formula for the left operand and a number formula for the right operand
     * and creates a boolean formula for the geq operation. If both abstract integers are concrete
     * integers, it creates a number formula for the left and right operands and creates a boolean
     * formula for the geq operation. If the type of the abstract integers is not supported, it
     * prints an error message and exits the program.
     *
     * @param var1 the first abstract integer.
     * @param var2 the second abstract integer.
     * @return the boolean greater than or equal formula.
     */
    public JmcBooleanFormula geq(AbstractInteger var1, AbstractInteger var2) {
        return makeBooleanFormula(var1, var2, InstructionType.GEQ);
    }

    /**
     * Creates a symbolic greater than or equal (geq) operation based on an abstract integer and an
     * integer value.
     *
     * <p>This method creates a symbolic geq operation based on an abstract integer and an integer
     * value. It creates a concrete integer object with the integer value and calls the {@link
     * #geq(AbstractInteger, AbstractInteger)} method with the abstract integer and the concrete
     * integer.
     *
     * @param var1 the abstract integer.
     * @param var2 the integer value.
     * @return the boolean greater than or equal formula.
     */
    public JmcBooleanFormula geq(AbstractInteger var1, int var2) {
        ConcreteInteger concreteInteger = new ConcreteInteger(var2);
        return geq(var1, concreteInteger);
    }

    /**
     * Creates a symbolic greater than or equal (geq) operation based on an integer value and an
     * abstract integer.
     *
     * <p>This method creates a symbolic geq operation based on an integer value and an abstract
     * integer by calling the {@link #geq(AbstractInteger, int)} method with the abstract integer
     * and the integer value.
     *
     * @param var1 the integer value.
     * @param var2 the abstract integer.
     * @return the boolean greater than or equal formula.
     */
    public JmcBooleanFormula geq(int var1, AbstractInteger var2) {
        return geq(var2, var1);
    }

    /**
     * Creates a symbolic greater than (gt) operation based on two abstract integers.
     *
     * <p>This method creates a symbolic gt operation based on two abstract integers. First, it
     * clears the integer variable map which stores the symbolic integer variable which are used in
     * the operation. Then, it creates a symbolic operation object and checks the type of the
     * abstract integers. If both abstract integers are symbolic integers, it creates an integer
     * formula for the left and right operands and creates a boolean formula for the gt operation.
     * If one of the abstract integers is a symbolic integer and the other is a concrete integer, it
     * creates an integer formula for the left operand and a number formula for the right operand
     * and creates a boolean formula for the gt operation. If both abstract integers are concrete
     * integers, it creates a number formula for the left and right operands and creates a boolean
     * formula for the gt operation. If the type of the abstract integers is not supported, it
     * prints an error message and exits the program.
     *
     * @param var1 the first abstract integer.
     * @param var2 the second abstract integer.
     * @return the boolean greater than formula.
     */
    public JmcBooleanFormula gt(AbstractInteger var1, AbstractInteger var2) {
        return makeBooleanFormula(var1, var2, InstructionType.GT);
    }

    /**
     * Creates a symbolic greater than (gt) operation based on an abstract integer and an integer
     * value.
     *
     * <p>This method creates a symbolic gt operation based on an abstract integer and an integer
     * value. It creates a concrete integer object with the integer value and calls the {@link
     * #gt(AbstractInteger, AbstractInteger)} method with the abstract integer and the concrete
     * integer.
     *
     * @param var1 the abstract integer.
     * @param var2 the integer value.
     * @return the boolean greater than formula.
     */
    public JmcBooleanFormula gt(AbstractInteger var1, int var2) {
        ConcreteInteger concreteInteger = new ConcreteInteger(var2);
        return gt(var1, concreteInteger);
    }

    /**
     * Creates a symbolic greater than (gt) operation based on an integer value and an abstract
     * integer.
     *
     * <p>This method creates a symbolic gt operation based on an integer value and an abstract
     * integer by calling the {@link #gt(AbstractInteger, int)} method with the abstract integer and
     * the integer value.
     *
     * @param var1 the integer value.
     * @param var2 the abstract integer.
     * @return the boolean greater than formula.
     */
    public JmcBooleanFormula gt(int var1, AbstractInteger var2) {
        return gt(var2, var1);
    }

    /**
     * Creates a symbolic less than or equal (leq) operation based on two abstract integers.
     *
     * <p>This method creates a symbolic leq operation based on two abstract integers. First, it
     * clears the integer variable map which stores the symbolic integer variable which are used in
     * the operation. Then, it creates a symbolic operation object and checks the type of the
     * abstract integers. If both abstract integers are symbolic integers, it creates an integer
     * formula for the left and right operands and creates a boolean formula for the leq operation.
     * If one of the abstract integers is a symbolic integer and the other is a concrete integer, it
     * creates an integer formula for the left operand and a number formula for the right operand
     * and creates a boolean formula for the leq operation. If both abstract integers are concrete
     * integers, it creates a number formula for the left and right operands and creates a boolean
     * formula for the leq operation. If the type of the abstract integers is not supported, it
     * prints an error message and exits the program.
     *
     * @param var1 the first abstract integer.
     * @param var2 the second abstract integer.
     * @return the boolean less than or equal formula.
     */
    public JmcBooleanFormula leq(AbstractInteger var1, AbstractInteger var2) {
        return makeBooleanFormula(var1, var2, InstructionType.LEQ);
    }

    /**
     * Creates a symbolic less than or equal (leq) operation based on an abstract integer and an
     * integer value.
     *
     * <p>This method creates a symbolic leq operation based on an abstract integer and an integer
     * value. It creates a concrete integer object with the integer value and calls the {@link
     * #leq(AbstractInteger, AbstractInteger)} method with the abstract integer and the concrete
     * integer.
     *
     * @param var1 the abstract integer.
     * @param var2 the integer value.
     * @return the boolean less than or equal formula.
     */
    public JmcBooleanFormula leq(AbstractInteger var1, int var2) {
        ConcreteInteger concreteInteger = new ConcreteInteger(var2);
        return leq(var1, concreteInteger);
    }

    /**
     * Creates a symbolic less than or equal (leq) operation based on an integer value and an
     * abstract integer.
     *
     * <p>This method creates a symbolic leq operation based on an integer value and an abstract
     * integer by calling the {@link #leq(AbstractInteger, int)} method with the abstract integer
     * and the integer value.
     *
     * @param var1 the integer value.
     * @param var2 the abstract integer.
     * @return the boolean less than or equal formula.
     */
    public JmcBooleanFormula leq(int var1, AbstractInteger var2) {
        return leq(var2, var1);
    }

    /**
     * Creates a symbolic less than (lt) operation based on two abstract integers.
     *
     * <p>This method creates a symbolic lt operation based on two abstract integers. First, it
     * clears the integer variable map which stores the symbolic integer variable which are used in
     * the operation. Then, it creates a symbolic operation object and checks the type of the
     * abstract integers. If both abstract integers are symbolic integers, it creates an integer
     * formula for the left and right operands and creates a boolean formula for the lt operation.
     * If one of the abstract integers is a symbolic integer and the other is a concrete integer, it
     * creates an integer formula for the left operand and a number formula for the right operand
     * and creates a boolean formula for the lt operation. If both abstract integers are concrete
     * integers, it creates a number formula for the left and right operands and creates a boolean
     * formula for the lt operation. If the type of the abstract integers is not supported, it
     * prints an error message and exits the program.
     *
     * @param var1 the first abstract integer.
     * @param var2 the second abstract integer.
     * @return the boolean less than formula.
     */
    public JmcBooleanFormula lt(AbstractInteger var1, AbstractInteger var2) {
        return makeBooleanFormula(var1, var2, InstructionType.LT);
    }

    /**
     * Creates a symbolic less than (lt) operation based on an abstract integer and an integer
     * value.
     *
     * <p>This method creates a symbolic lt operation based on an abstract integer and an integer
     * value. It creates a concrete integer object with the integer value and calls the {@link
     * #lt(AbstractInteger, AbstractInteger)} method with the abstract integer and the concrete
     * integer.
     *
     * @param var1 the abstract integer.
     * @param var2 the integer value.
     * @return the boolean less than formula.
     */
    public JmcBooleanFormula lt(AbstractInteger var1, int var2) {
        ConcreteInteger concreteInteger = new ConcreteInteger(var2);
        return lt(var1, concreteInteger);
    }

    /**
     * Creates a symbolic less than (lt) operation based on an integer value and an abstract
     * integer.
     *
     * <p>This method creates a symbolic lt operation based on an integer value and an abstract
     * integer by calling the {@link #lt(AbstractInteger, int)} method with the abstract integer and
     * the integer value.
     *
     * @param var1 the integer value.
     * @param var2 the abstract integer.
     * @return the boolean less than formula.
     */
    public JmcBooleanFormula lt(int var1, AbstractInteger var2) {
        return lt(var2, var1);
    }

    public JmcBooleanFormula distinct(List<AbstractInteger> vars) {
        integerVariableMap = new HashMap<>();
        JmcBooleanFormula formula = new JmcBooleanFormula();

        ArrayList<NumeralFormula.IntegerFormula> formulas = new ArrayList<>();
        for (AbstractInteger var : vars) {
            formulas.add(makeIntegerFormula(var));
        }
        org.sosy_lab.java_smt.api.BooleanFormula distinctFormula = imgr.distinct(formulas);

        formula.setFormula(distinctFormula);
        formula.setIntegerVariableMap(integerVariableMap);

        // Explicitly upcast List<AbstractInteger> to List<SymbolicOperand>
        List<SymbolicOperand> operandList =
                vars.stream().map(var -> (SymbolicOperand) var).collect(Collectors.toList());
        formula.setJmcFormula(operandList, InstructionType.DISTINCT);
        return formula;
    }

    private JmcBooleanFormula makeBooleanFormula(
            AbstractInteger var1, AbstractInteger var2, InstructionType operator) {
        integerVariableMap = new HashMap<>();
        JmcBooleanFormula formula = new JmcBooleanFormula();

        NumeralFormula.IntegerFormula leftOperand = makeIntegerFormula(var1);
        NumeralFormula.IntegerFormula rightOperand = makeIntegerFormula(var2);
        org.sosy_lab.java_smt.api.BooleanFormula arithmeticFormula =
                makeArithmeticFormula(leftOperand, rightOperand, operator);

        formula.setFormula(arithmeticFormula);
        formula.setIntegerVariableMap(integerVariableMap);
        formula.setJmcFormula(var1, var2, operator);

        return formula;
    }

    private org.sosy_lab.java_smt.api.BooleanFormula makeArithmeticFormula(
            NumeralFormula.IntegerFormula leftOperand,
            NumeralFormula.IntegerFormula rightOperand,
            InstructionType operator) {
        switch (operator) {
            case EQ:
                return imgr.equal(leftOperand, rightOperand);
            case NEQ:
                return bmgr.not(imgr.equal(leftOperand, rightOperand));
            case GEQ:
                return imgr.greaterOrEquals(leftOperand, rightOperand);
            case GT:
                return imgr.greaterThan(leftOperand, rightOperand);
            case LEQ:
                return imgr.lessOrEquals(leftOperand, rightOperand);
            case LT:
                return imgr.lessThan(leftOperand, rightOperand);
            default:
                System.out.println("[Symbolic Execution] Unsupported operator [" + operator + "]");
                System.exit(0);
                return null;
        }
    }

    private NumeralFormula.IntegerFormula makeIntegerFormula(AbstractInteger abstInteger) {
        if (abstInteger instanceof ConcreteInteger concreteInteger) {
            return handleConcrInt(concreteInteger);
        } else {
            return handleSymbInt((SymbolicInteger) abstInteger.read());
        }
    }

    private NumeralFormula.IntegerFormula handleSymbInt(SymbolicInteger symbolicInteger) {
        if (symbolicInteger.getEval() == null) {
            return findVariable(symbolicInteger.getName());
        }
        NumeralFormula.IntegerFormula leftOperand =
                symbolicInteger.getEval().getLeft() instanceof SymbolicInteger
                        ? makeIntegerFormula(symbolicInteger.getEval().getLeft())
                        : imgr.makeNumber(symbolicInteger.getEval().getLeft().getValue());
        NumeralFormula.IntegerFormula rightOperand =
                symbolicInteger.getEval().getRight() instanceof SymbolicInteger
                        ? makeIntegerFormula(symbolicInteger.getEval().getRight())
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
                System.out.println(
                        "[Symbolic Execution] Unsupported operator ["
                                + symbolicInteger.getEval().getOperator()
                                + "]");
                System.exit(0);
                return null;
        }
    }

    private NumeralFormula.IntegerFormula handleConcrInt(ConcreteInteger concreteInteger) {
        return imgr.makeNumber(concreteInteger.getValue());
    }

    private NumeralFormula.IntegerFormula findVariable(String name) {
        if (integerVariableMap.containsKey(name)) {
            return integerVariableMap.get(name);
        } else {
            SymIntVariable symIntVariable = JmcSymbolic.getSymIntVariable(name);
            integerVariableMap.put(name, symIntVariable.getVar());
            return symIntVariable.getVar();
        }
    }
}
