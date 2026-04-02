package org.mpi_sws.jmc.api.symbolic.array;

import org.sosy_lab.java_smt.api.ArrayFormula;
import org.sosy_lab.java_smt.api.NumeralFormula;

// TODO : Complete the implementation

/**
 * The {@link SymArrayVariable} class is used to represent a symbolic array variable.
 */
public class SymArrayVariable {

    /**
     * The symbolic array variable. This is represented using an {@link ArrayFormula} from the JavaSMT library.
     */
    private final ArrayFormula<NumeralFormula.IntegerFormula, NumeralFormula.IntegerFormula> var;

    /**
     * Constructor to initialize the symbolic array variable.
     *
     * @param var the symbolic array variable.
     */
    public SymArrayVariable(ArrayFormula<NumeralFormula.IntegerFormula, NumeralFormula.IntegerFormula> var) {
        this.var = var;
    }

    /**
     * Clones the symbolic array variable.
     *
     * @return a clone of the symbolic array variable.
     */
    public SymArrayVariable clone() {
        SymArrayVariable copy = new SymArrayVariable(this.var);
        return copy;
    }
}
