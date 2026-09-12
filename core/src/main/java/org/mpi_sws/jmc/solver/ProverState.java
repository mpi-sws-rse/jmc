package org.mpi_sws.jmc.solver;

import org.mpi_sws.jmc.api.symbolic.array.SymArrayVariable;
import org.mpi_sws.jmc.api.symbolic.bool.SymBoolVariable;
import org.mpi_sws.jmc.api.symbolic.integer.SymIntVariable;
import org.sosy_lab.java_smt.api.ProverEnvironment;

import java.util.HashMap;
import java.util.Map;

/**
 * A single SMT prover context together with the symbolic variables declared in it.
 *
 * <p>ConDpor keeps one {@code ProverState} per sub-exploration (each backward revisit clones the
 * current one). It bundles a JavaSMT {@link ProverEnvironment} — the actual constraint stack — with
 * the maps of symbolic variable names to their solver variables, so switching the active prover also
 * switches the variable set. Managed by {@code IncrementalSolver} (created, pooled, and reused).
 */
public class ProverState {

    /** The JavaSMT prover environment holding this context's constraint stack. */
    public ProverEnvironment prover;
    /** Symbolic integer variables declared in this context, by name. */
    public Map<String, SymIntVariable> symIntVariableMap = new HashMap<>();
    /** Symbolic boolean variables declared in this context, by name. */
    public Map<String, SymBoolVariable> symBoolVariableMap = new HashMap<>();
    /** Symbolic array variables declared in this context (arrays are not currently supported). */
    public Map<String, SymArrayVariable> symArrayVariableHashMap = new HashMap<>();

    /**
     * Creates a prover state around the given JavaSMT prover environment.
     *
     * @param prover the prover environment.
     */
    public ProverState(ProverEnvironment prover) {
        this.prover = prover;
    }

    /** Clears all symbolic-variable maps (called when the prover is returned to the pool). */
    public void clear() {
        symIntVariableMap.clear();
        symBoolVariableMap.clear();
        symArrayVariableHashMap.clear();
    }
}
