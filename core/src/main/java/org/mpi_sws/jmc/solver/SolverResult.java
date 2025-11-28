package org.mpi_sws.jmc.solver;

public class SolverResult {

    private final boolean result;
    private final boolean isNegatable;

    public SolverResult(boolean result, boolean isNegatable) {
        this.result = result;
        this.isNegatable = isNegatable;
    }

    public boolean result() {
        return result;
    }

    public boolean isNegatable() {
        return isNegatable;
    }
}
