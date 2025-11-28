package org.mpi_sws.jmc.symbolic.array;

import org.sosy_lab.java_smt.api.ArrayFormula;
import org.sosy_lab.java_smt.api.NumeralFormula;

// TODO : Complete the implementation
public class SymArrayVariable {

    private final ArrayFormula<NumeralFormula.IntegerFormula, NumeralFormula.IntegerFormula> var;

    public SymArrayVariable(ArrayFormula<NumeralFormula.IntegerFormula, NumeralFormula.IntegerFormula> var) {
        this.var = var;
    }

    public SymArrayVariable clone() {
        SymArrayVariable copy = new SymArrayVariable(this.var);
        return copy;
    }
}
