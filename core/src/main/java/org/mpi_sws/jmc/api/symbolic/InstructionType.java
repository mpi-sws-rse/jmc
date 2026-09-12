package org.mpi_sws.jmc.api.symbolic;

/**
 * The operators used to build symbolic expressions and constraints.
 *
 * <p>The arithmetic operators combine integer operands (in an {@code ArithmeticStatement}); the
 * relational operators turn integers into a boolean formula (in {@code ArithmeticFormula}); the
 * logical operators combine boolean formulas (in {@code PropositionalFormula}). {@link
 * JmcConcreteFormula} interprets these same operators over concrete values.
 */
public enum InstructionType {
    /** Integer addition. */
    ADD,
    /** Integer subtraction. */
    SUB,
    /** Integer multiplication. */
    MUL,
    /** Integer division. */
    DIV,
    /** Integer modulo. */
    MOD,
    /** Logical negation. */
    NOT,
    /** Logical conjunction. */
    AND,
    /** Logical disjunction. */
    OR,
    /** Logical implication. */
    IMPLIES,
    /** Logical biconditional (if-and-only-if). */
    IFF,
    /** Logical exclusive-or. */
    XOR,
    /** Equality. */
    EQ,
    /** Inequality. */
    NEQ,
    /** Less-than. */
    LT,
    /** Greater-than. */
    GT,
    /** Less-than-or-equal. */
    LEQ,
    /** Greater-than-or-equal. */
    GEQ,
    /** All operands pairwise distinct. */
    DISTINCT,
    /** An atomic literal (a variable used directly as a formula). */
    ATOM,
}
