package org.mpisws.symbolic;

import java.util.Random;
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula;

public class SymIntVariable {

  private IntegerFormula var;

  public int value;

  public SymIntVariable(IntegerFormula var) {
    this.var = var;
    Random random = new Random();
    this.value = random.nextInt();
  }

  public IntegerFormula getVar() {
    return var;
  }

  public void setVar(IntegerFormula var) {
    this.var = var;
  }

  public void setValue(int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
