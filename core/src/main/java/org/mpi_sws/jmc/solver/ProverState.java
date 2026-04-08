package org.mpi_sws.jmc.solver;

import org.mpi_sws.jmc.api.symbolic.array.SymArrayVariable;
import org.mpi_sws.jmc.api.symbolic.bool.SymBoolVariable;
import org.mpi_sws.jmc.api.symbolic.integer.SymIntVariable;
import org.sosy_lab.java_smt.api.ProverEnvironment;

import java.util.HashMap;
import java.util.Map;

public class ProverState {

    public ProverEnvironment prover;
    public Map<String, SymIntVariable> symIntVariableMap = new HashMap<>();
    public Map<String, SymBoolVariable> symBoolVariableMap = new HashMap<>();
    public Map<String, SymArrayVariable> symArrayVariableHashMap = new HashMap<>();

    public ProverState(ProverEnvironment prover) {
        this.prover = prover;
    }

    public void clear() {
        symIntVariableMap.clear();
        symBoolVariableMap.clear();
        symArrayVariableHashMap.clear();
    }
}
