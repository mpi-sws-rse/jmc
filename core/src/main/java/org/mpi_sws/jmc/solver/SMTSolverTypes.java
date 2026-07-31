package org.mpi_sws.jmc.solver;

/**
 * The SMT solver backends selectable for ConDpor's symbolic reasoning.
 *
 * <p>Each entry (except {@link #OFF}) maps to a JavaSMT solver; {@code SymbolicSolver.findSolverType}
 * performs the mapping. The value is chosen from the strategy's {@code solver} option;
 * {@link #OFF} disables symbolic execution (plain Trust). Z3 is the best-tested backend.
 */
public enum SMTSolverTypes {
    /** The OpenSMT solver. */
    OPENSMT,
    /** The MathSAT5 solver. */
    MATHSAT5,
    /** The SMTInterpol solver. */
    SMTINTERPOL,
    /** The Z3 solver (recommended / best-tested). */
    Z3,
    /** The Princess solver. */
    PRINCESS,
    /** The Boolector solver. */
    BOOLECTOR,
    /** The CVC4 solver. */
    CVC4,
    /** The CVC5 solver. */
    CVC5,
    /** The Yices2 solver. */
    YICES2,
    /** Symbolic execution disabled (plain Trust). */
    OFF
}
