package org.mpisws.concurrent.programs.nondet.stack.elimination;

import org.mpisws.symbolic.ArithmeticFormula;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.symbolic.SymbolicOperation;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.Utils;

public class RangePolicy {

    public SymbolicInteger range = new SymbolicInteger(false);

    public RangePolicy() throws JMCInterruptException {
        ArithmeticFormula f = new ArithmeticFormula();
        SymbolicOperation op1 = f.geq(range, 0);
        Utils.assume(op1); // assume range >= 0
    }

    public SymbolicInteger getRange() {
        return range;
    }
}
