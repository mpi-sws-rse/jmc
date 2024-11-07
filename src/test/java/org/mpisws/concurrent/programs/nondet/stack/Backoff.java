package org.mpisws.concurrent.programs.nondet.stack;

import org.mpisws.symbolic.*;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.Utils;

public class Backoff {

    public final SymbolicInteger minDelay, maxDelay;
    public SymbolicInteger limit;

    public Backoff(SymbolicInteger min, SymbolicInteger max) throws JMCInterruptException {
        minDelay = min;
        maxDelay = max;
        ArithmeticFormula f = new ArithmeticFormula();
        SymbolicOperation op = f.leq(minDelay, maxDelay);
        Utils.assume(op); // assert minDelay <= maxDelay;
        limit = new SymbolicInteger(true);
        limit.assign(minDelay);
    }

    public void backoff() throws JMCInterruptException {
        SymbolicInteger delay = new SymbolicInteger(false);
        ArithmeticFormula f = new ArithmeticFormula();
        SymbolicOperation op1 = f.lt(delay, limit);
        Utils.assume(op1); // int delay = ThreadLocalRandom.current().nextInt(limit);

        SymbolicInteger limit2 = new SymbolicInteger(false);
        ArithmeticStatement s1 = new ArithmeticStatement();
        s1.mul(limit, 2);
        limit2.assign(s1); // limit2 = 2 * limit;

        SymbolicOperation op2 = f.lt(limit2, maxDelay);
        SymbolicOperation op3 = f.gt(limit2, maxDelay);
        SymbolicOperation op4 = f.eq(limit2, maxDelay);
        SymbolicFormula f2 = new SymbolicFormula();
        if (f2.evaluate(op2)) {
            limit.assign(limit2);
        } else if (f2.evaluate(op3)) {
            limit.assign(maxDelay);
        } else if (f2.evaluate(op4)) {
            limit.assign(maxDelay);
        } // limit = Math.min(maxDelay, 2 * limit);
        // Thread.sleep(delay);
    }
}
