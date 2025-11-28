package org.mpi_sws.jmc.symbolic.integer;

import org.mpi_sws.jmc.symbolic.InstructionType;

/**
 * ArithmeticStatement class represents an arithmetic operation between two abstract integers.
 * It can be used to perform addition, subtraction, multiplication, division, and modulus operations.
 */
public class ArithmeticStatement {
    /**
     * The left operand of the arithmetic operation.
     */
    private AbstractInteger left;

    /**
     * The right operand of the arithmetic operation.
     */
    private AbstractInteger right;

    /**
     * The type of arithmetic operation to be performed.
     */
    private InstructionType operator;

    /**
     * Default constructor for ArithmeticStatement.
     * Initializes the left and right operands to null and the operator to null.
     */
    public ArithmeticStatement() {

    }

    /**
     * Constructor for ArithmeticStatement.
     * Initializes the left and right operands and the operator.
     *
     * @param var1     The first operand (AbstractInteger).
     * @param var2     The second operand (AbstractInteger).
     * @param operator The type of arithmetic operation to be performed.
     */
    public ArithmeticStatement(AbstractInteger var1, AbstractInteger var2, InstructionType operator) {
        this.left = var1.read();
        this.right = var2.read();
        this.operator = operator;
    }

    /**
     * Constructor for ArithmeticStatement.
     * Initializes the left operand to a ConcreteInteger and the right operand to an AbstractInteger.
     *
     * @param var1     The first operand (ConcreteInteger).
     * @param var2     The second operand (AbstractInteger).
     * @param operator The type of arithmetic operation to be performed.
     */
    public ArithmeticStatement(AbstractInteger var1, int var2, InstructionType operator) {
        this.left = var1.read();
        this.right = new ConcreteInteger(var2);
        this.operator = operator;
    }

    /**
     * Constructor for ArithmeticStatement.
     * Initializes the left operand to a ConcreteInteger and the right operand to an AbstractInteger.
     *
     * @param var1     The first operand (int).
     * @param var2     The second operand (AbstractInteger).
     * @param operator The type of arithmetic operation to be performed.
     */
    public ArithmeticStatement(int var1, AbstractInteger var2, InstructionType operator) {
        this.left = new ConcreteInteger(var1);
        this.right = var2.read();
        this.operator = operator;
    }

    /**
     * Performs addition between two AbstractInteger operands.
     *
     * @param var1 The first operand (AbstractInteger).
     * @param var2 The second operand (AbstractInteger).
     */
    public void add(AbstractInteger var1, AbstractInteger var2) {
        this.left = var1.read();
        this.right = var2.read();
        this.operator = InstructionType.ADD;
    }

    /**
     * Performs addition between an AbstractInteger and an int operand.
     *
     * @param var1 The first operand (AbstractInteger).
     * @param var2 The second operand (int).
     */
    public void add(AbstractInteger var1, int var2) {
        this.left = var1.read();
        this.right = new ConcreteInteger(var2);
        this.operator = InstructionType.ADD;
    }

    /**
     * Performs addition between an int and an AbstractInteger operand.
     *
     * @param var1 The first operand (int).
     * @param var2 The second operand (AbstractInteger).
     */
    public void add(int var1, AbstractInteger var2) {
        this.left = new ConcreteInteger(var1);
        this.right = var2.read();
        this.operator = InstructionType.ADD;
    }

    /**
     * Performs subtraction between two AbstractInteger operands.
     *
     * @param var1 The first operand (AbstractInteger).
     * @param var2 The second operand (AbstractInteger).
     */
    public void sub(AbstractInteger var1, AbstractInteger var2) {
        this.left = var1.read();
        this.right = var2.read();
        this.operator = InstructionType.SUB;
    }

    /**
     * Performs subtraction between an AbstractInteger and an int operand.
     *
     * @param var1 The first operand (AbstractInteger).
     * @param var2 The second operand (int).
     */
    public void sub(AbstractInteger var1, int var2) {
        this.left = var1.read();
        this.right = new ConcreteInteger(var2);
        this.operator = InstructionType.SUB;
    }

    /**
     * Performs subtraction between an int and an AbstractInteger operand.
     *
     * @param var1 The first operand (int).
     * @param var2 The second operand (AbstractInteger).
     */
    public void sub(int var1, AbstractInteger var2) {
        this.left = new ConcreteInteger(var1);
        this.right = var2.read();
        this.operator = InstructionType.SUB;
    }

    /**
     * Performs multiplication between two AbstractInteger operands.
     *
     * @param var1 The first operand (AbstractInteger).
     * @param var2 The second operand (AbstractInteger).
     */
    public void mul(AbstractInteger var1, AbstractInteger var2) {
        this.left = var1.read();
        this.right = var2.read();
        this.operator = InstructionType.MUL;
    }

    /**
     * Performs multiplication between an AbstractInteger and an int operand.
     *
     * @param var1 The first operand (AbstractInteger).
     * @param var2 The second operand (int).
     */
    public void mul(AbstractInteger var1, int var2) {
        this.left = var1.read();
        this.right = new ConcreteInteger(var2);
        this.operator = InstructionType.MUL;
    }

    /**
     * Performs multiplication between an int and an AbstractInteger operand.
     *
     * @param var1 The first operand (int).
     * @param var2 The second operand (AbstractInteger).
     */
    public void mul(int var1, AbstractInteger var2) {
        this.left = new ConcreteInteger(var1);
        this.right = var2.read();
        this.operator = InstructionType.MUL;
    }

    /**
     * Performs division between two AbstractInteger operands.
     *
     * @param var1 The first operand (AbstractInteger).
     * @param var2 The second operand (AbstractInteger).
     */
    public void div(AbstractInteger var1, AbstractInteger var2) {
        this.left = var1.read();
        this.right = var2.read();
        this.operator = InstructionType.DIV;
    }

    /**
     * Performs division between an AbstractInteger and an int operand.
     *
     * @param var1 The first operand (AbstractInteger).
     * @param var2 The second operand (int).
     */
    public void div(AbstractInteger var1, int var2) {
        this.left = var1.read();
        this.right = new ConcreteInteger(var2);
        this.operator = InstructionType.DIV;
    }

    /**
     * Performs division between an int and an AbstractInteger operand.
     *
     * @param var1 The first operand (int).
     * @param var2 The second operand (AbstractInteger).
     */
    public void div(int var1, AbstractInteger var2) {
        this.left = new ConcreteInteger(var1);
        this.right = var2.read();
        this.operator = InstructionType.DIV;
    }

    /**
     * Performs modulus between two AbstractInteger operands.
     *
     * @param var1 The first operand (AbstractInteger).
     * @param var2 The second operand (AbstractInteger).
     */
    public void mod(AbstractInteger var1, AbstractInteger var2) {
        this.left = var1.read();
        this.right = var2.read();
        this.operator = InstructionType.MOD;
    }

    /**
     * Performs modulus between an AbstractInteger and an int operand.
     *
     * @param var1 The first operand (AbstractInteger).
     * @param var2 The second operand (int).
     */
    public void mod(AbstractInteger var1, int var2) {
        this.left = var1.read();
        this.right = new ConcreteInteger(var2);
        this.operator = InstructionType.MOD;
    }

    /**
     * Performs modulus between an int and an AbstractInteger operand.
     *
     * @param var1 The first operand (int).
     * @param var2 The second operand (AbstractInteger).
     */
    public void mod(int var1, AbstractInteger var2) {
        this.left = new ConcreteInteger(var1);
        this.right = var2.read();
        this.operator = InstructionType.MOD;
    }

    /**
     * Clones the current ArithmeticStatement object.
     *
     * @return A new ArithmeticStatement object with the same values as the current object.
     */
    public ArithmeticStatement clone() {
        ArithmeticStatement copy = new ArithmeticStatement();
        copy.left = left.clone();
        copy.right = right.clone();
        copy.operator = operator;
        return copy;
    }

    /**
     * Returns the left operand of the arithmetic operation.
     *
     * @return The left operand (AbstractInteger).
     */
    public AbstractInteger getLeft() {
        return this.left;
    }

    /**
     * Returns the right operand of the arithmetic operation.
     *
     * @return The right operand (AbstractInteger).
     */
    public AbstractInteger getRight() {
        return this.right;
    }

    /**
     * Returns the type of arithmetic operation to be performed.
     *
     * @return The type of arithmetic operation (InstructionType).
     */
    public InstructionType getOperator() {
        return this.operator;
    }

    /**
     * Sets the left operand of the arithmetic operation.
     *
     * @param left The left operand (AbstractInteger).
     */
    public void setLeft(AbstractInteger left) {
        this.left = left;
    }

    /**
     * Sets the right operand of the arithmetic operation.
     *
     * @param right The right operand (AbstractInteger).
     */
    public void setRight(AbstractInteger right) {
        this.right = right;
    }

    /**
     * Sets the type of arithmetic operation to be performed.
     *
     * @param operator The type of arithmetic operation (InstructionType).
     */
    public void setOperator(InstructionType operator) {
        this.operator = operator;
    }
}
