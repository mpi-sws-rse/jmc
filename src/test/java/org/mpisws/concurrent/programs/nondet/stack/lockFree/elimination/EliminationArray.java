package org.mpisws.concurrent.programs.nondet.stack.lockFree.elimination;

import java.util.ArrayList;
import java.util.concurrent.TimeoutException;
import org.mpisws.symbolic.ArithmeticFormula;
import org.mpisws.symbolic.SymbolicFormula;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.symbolic.SymbolicOperation;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.Utils;

public class EliminationArray<V> {

  private final SymbolicInteger duration = new SymbolicInteger(false);
  private final int MaxDuration = 1000;
  public ArrayList<LockFreeExchanger<V>> exchanger = new ArrayList<>();
  private final int MAX_CAPACITY = 3;
  public SymbolicInteger capacityArray = new SymbolicInteger(false);

  public EliminationArray(SymbolicInteger capacity) throws JMCInterruptException {
    ArithmeticFormula f = new ArithmeticFormula();
    SymbolicOperation op0 = f.gt(capacity, 0);
    Utils.assume(op0); // assume capacity > 0

    SymbolicOperation op1 = f.leq(capacity, MAX_CAPACITY);
    Utils.assume(op1); // assume capacity <= MAX_CAPACITY

    SymbolicOperation op2 = f.gt(duration, 0);
    Utils.assume(op2); // assume duration > 0

    SymbolicOperation op3 = f.lt(duration, MaxDuration);
    Utils.assume(op3); // assume duration < MaxDuration

    int i = 0;
    SymbolicOperation op4 = f.gt(capacity, i);
    SymbolicFormula sf = new SymbolicFormula();
    for (i = 0; sf.evaluate(op4); ) {
      exchanger.add(i, new LockFreeExchanger<V>());
      i++;
      op4 = f.gt(capacity, i);
    }
    capacityArray.assign(capacity);
  }

  public V visit(V value, SymbolicInteger range) throws TimeoutException, JMCInterruptException {
    ArithmeticFormula f = new ArithmeticFormula();
    SymbolicOperation op1 = f.lt(range, capacityArray);
    Utils.assume(op1); // assume range < capacityArray

    SymbolicInteger slot = new SymbolicInteger(false);

    SymbolicOperation op2 = f.geq(slot, 0);
    Utils.assume(op2); // assume slot >= 0

    SymbolicOperation op3 = f.lt(slot, range);
    Utils.assume(op3); // assume slot < range
    return exchanger.get(slot.getIntValue()).exchange(value, duration);
  }
}
