package org.mpisws.solver;

import org.mpisws.symbolic.SymBoolVariable;
import org.mpisws.symbolic.SymIntVariable;
import org.sosy_lab.java_smt.api.ProverEnvironment;

import java.util.HashMap;
import java.util.Map;

public class ProverState {

    public ProverEnvironment prover;
    public HashMap<String, SymIntVariable> symIntVariableMap = new HashMap<>();
    public HashMap<String, SymBoolVariable> symBoolVariableMap = new HashMap<>();

    public ProverState(ProverEnvironment prover) {
        this.prover = prover;
    }

    public void clear() {
        symIntVariableMap.clear();
        symBoolVariableMap.clear();
    }
}
