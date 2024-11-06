package org.mpisws.concurrent.programs.nondet.loop;

import org.mpisws.symbolic.ArithmeticFormula;
import org.mpisws.symbolic.SymbolicOperation;
import org.mpisws.util.concurrent.Utils;

public class AssertThread extends Thread {

  Numbers numbers;

  public AssertThread(Numbers numbers) {
    this.numbers = numbers;
  }

  @Override
  public void run() {
    ArithmeticFormula f = new ArithmeticFormula();
    // SymbolicOperation op = f.gt(numbers.n, numbers.x);
    SymbolicOperation op = f.geq(numbers.n, numbers.x);
    Utils.assertion(op, "AssertThread failed");
  }
}
