package org.mpisws.solver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.runtime.RuntimeEnvironment;
import org.mpisws.symbolic.SymbolicOperation;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.api.SolverContext;
import org.sosy_lab.java_smt.api.SolverException;

import java.math.BigInteger;

// TODO: should be a decorator on the SimpleSolver. Using inheritance makes it harder to use later
// on.
public class IncrementalSolver extends SymbolicSolver {

  private static final Logger LOGGER = LogManager.getLogger(IncrementalSolver.class);

  ProverEnvironment prover;

  public IncrementalSolver() {
    super();
    prover = context.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS);
  }

  public IncrementalSolver(SMTSolverTypes type) {
    super(type);
    prover = context.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS);
  }

  @Override
  public void computeNewSymbolicOperationRequest(SymbolicOperation symbolicOperation) {
    boolean concreteEval = symbolicOperation.concreteEvaluation();
    boolean symbolicEval;
    if (concreteEval) {
      symbolicEval = disSolveSymbolicFormula(symbolicOperation);
    } else {
      symbolicEval = solveSymbolicFormula(symbolicOperation);
    }
    bothSatUnsat = symbolicEval;
    pop();
    if (concreteEval) {
      push(symbolicOperation);
      RuntimeEnvironment.solverResult = true;
    } else {
      push(negateFormula(symbolicOperation.getFormula()));
      RuntimeEnvironment.solverResult = false;
    }
  }

  /**
   * @param symbolicOperation
   */
  @Override
  public void computeNewSymAssumeOperationRequest(SymbolicOperation symbolicOperation) {
    boolean concreteEval = symbolicOperation.concreteEvaluation();
    if (concreteEval) {
      RuntimeEnvironment.solverResult = true;
      push(symbolicOperation);
    } else {
      boolean symbolicEval = solveSymbolicFormula(symbolicOperation);
      RuntimeEnvironment.solverResult = symbolicEval;
      if (symbolicEval) {
        updateModel();
      } else {
        pop();
      }
    }
  }

  /**
   * @param symbolicOperation
   */
  @Override
  public void computeSymbolicAssertOperationRequest(SymbolicOperation symbolicOperation) {
    boolean concreteEval = symbolicOperation.concreteEvaluation();
    if (concreteEval) {
      RuntimeEnvironment.solverResult = true;
    } else {
      boolean sat = solveSymbolicFormula(symbolicOperation);
      if (sat) {
        RuntimeEnvironment.solverResult = true;
        pop();
      } else {
        RuntimeEnvironment.solverResult = false;
        push(negateFormula(symbolicOperation.getFormula()));
      }
    }
  }

  private void updateModel() {
    if (model != null) {
      model
          .iterator()
          .forEachRemaining(
              entry -> {
                LOGGER.debug("Model Entry: {} : {}", entry.getKey(), entry.getValue());
                // The key is a string like className@address. extract the class Name
                String symbolicType = entry.getKey().toString().split("@")[0];
                if (symbolicType.equals("SymbolicBoolean")) {
                  symBoolVariableMap
                      .get(entry.getKey().toString())
                      .setValue((Boolean) entry.getValue());
                } else if (symbolicType.equals("SymbolicInteger")) {
                  if (entry.getValue() instanceof BigInteger) {
                    symIntVariableMap
                        .get(entry.getKey().toString())
                        .setValue(((BigInteger) entry.getValue()).intValue());
                  } else {
                    symIntVariableMap
                        .get(entry.getKey().toString())
                        .setValue((Integer) entry.getValue());
                  }
                } else {
                  throw new RuntimeException("[Incremental Solver Message] Unknown Symbolic Type");
                }
              });
    }
  }

  /**
   * @param symbolicOperation
   */
  @Override
  public void computeRandomlyNewSymbolicOperationRequest(SymbolicOperation symbolicOperation) {
    boolean concreteEval = symbolicOperation.concreteEvaluation();
    if (concreteEval) {
      boolean sat = true;
      boolean unsat = disSolveSymbolicFormula(symbolicOperation);
      pop();
      bothSatUnsat = unsat;
      RuntimeEnvironment.solverResult = pickSatOrUnsat(sat, unsat);
    } else {
      boolean unsat = true;
      boolean sat = solveSymbolicFormula(symbolicOperation);
      pop();
      bothSatUnsat = sat;
      RuntimeEnvironment.solverResult = pickSatOrUnsat(sat, unsat);
    }

    if (RuntimeEnvironment.solverResult) {
      push(symbolicOperation);
    } else {
      push(negateFormula(symbolicOperation.getFormula()));
    }
  }

  /** */
  @Override
  public void solveAndUpdateModelSymbolicVariables() {
    if (prover.size() > 0) {
      try {
        boolean isUnsat = prover.isUnsat();
        if (!isUnsat) {
          model = prover.getModel();
          updateModel();
        } else {
          throw new RuntimeException("[Incremental Solver Message] The formula is unsatisfiable");
        }
      } catch (SolverException e) {
        throw new RuntimeException(e);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * @param thread
   * @param symbolicOperation
   */
  public void deprecatedComputeNewSymbolicOperationRequest(
      Thread thread, SymbolicOperation symbolicOperation) {
    boolean sat = solveSymbolicFormula(symbolicOperation);
    pop();
    boolean unSat = disSolveSymbolicFormula(symbolicOperation);
    bothSatUnsat = sat && unSat;
    if (unSat) {
      RuntimeEnvironment.solverResult = false;
    } else {
      pop();
      push(symbolicOperation);
      RuntimeEnvironment.solverResult = true;
    }
  }

  @Override
  public void pop() {
    prover.pop();
  }

  @Override
  public void push() {
    try {
      prover.push();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void push(BooleanFormula formula) {
    try {
      prover.push(formula);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void push(SymbolicOperation operation) {
    push(operation.getFormula());
  }

  @Override
  protected boolean solver(BooleanFormula formula) {
    try {
      LOGGER.debug("Solving the formula: {}", formula.toString());
      prover.push(formula);
      boolean isUnsat = prover.isUnsat();
      if (!isUnsat) {
        model = prover.getModel();
        LOGGER.debug("The formula is satisfiable");
        return true;
      } else {
        LOGGER.debug("The formula is unsatisfiable");
        return false;
      }
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } catch (SolverException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void updatePathSymbolicOperations(SymbolicOperation symbolicOperation) {}
}
