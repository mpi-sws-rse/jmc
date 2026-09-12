package org.mpi_sws.jmc.api.symbolic;

/**
 * Marker interface for anything that can appear as an operand in a symbolic formula.
 *
 * <p>It is implemented by the symbolic value types ({@code AbstractInteger}, {@code
 * AbstractBoolean}) and by {@code JmcBooleanFormula}, so that the formula builders and the concrete
 * evaluator ({@link JmcConcreteFormula}) can treat variables and sub-formulas uniformly.
 */
public interface SymbolicOperand {
}
