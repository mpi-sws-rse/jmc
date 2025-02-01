package org.mpisws.symbolic;

import org.sosy_lab.java_smt.api.ArrayFormula;
import org.sosy_lab.java_smt.api.NumeralFormula;

public class SymArrayVariable {

    private final ArrayFormula<NumeralFormula.IntegerFormula, NumeralFormula.IntegerFormula> var;

    public SymArrayVariable(ArrayFormula<NumeralFormula.IntegerFormula, NumeralFormula.IntegerFormula> var) {
        this.var = var;
    }

    public SymArrayVariable deepCopy() {
        SymArrayVariable copy = new SymArrayVariable(this.var);
        return copy;
    }
}
