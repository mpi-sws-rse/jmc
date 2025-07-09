package org.mpisws.solver;

import org.mpisws.runtime.RuntimeEnvironment;
import org.mpisws.symbolic.SymbolicOperation;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.api.SolverContext;
import org.sosy_lab.java_smt.api.SolverException;

import java.math.BigInteger;
import java.util.Stack;


// TODO: should be a decorator on the SimpleSolver. Using inheritance makes it harder to use later on.
public class IncrementalSolver extends SymbolicSolver {

    public ProverEnvironment prover;
    public int proverId;

    public IncrementalSolver() {
        super();
        //prover = context.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS);
    }

    public IncrementalSolver(SMTSolverTypes type) {
        super(type);
        //prover = context.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS);
    }

    @Override
    public void resetProver() {
        //System.out.println("[Incremental Symbolic Solver Message] : Resetting the prover");
        long startTime = System.nanoTime();
        if (prover != null) {
            while (prover.size() > 0) {
                pop();
            }
//            prover.close();
//            prover = context.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS);
            //stack.clear();
        }
        long endTime = System.nanoTime();
        RuntimeEnvironment.incSolverTime(endTime - startTime);
    }

    /**
     * @return
     */
    @Override
    public int size() {
        return prover.size();
    }

    @Override
    public void computeNewSymbolicOperationRequest(SymbolicOperation symbolicOperation) {
        long startTime = System.nanoTime();
        boolean concreteEval = symbolicOperation.concreteEvaluation();
        long endTime = System.nanoTime();
        RuntimeEnvironment.incSolverTime(endTime - startTime);
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
        long startTime = System.nanoTime();
        boolean concreteEval = symbolicOperation.concreteEvaluation();
        long endTime = System.nanoTime();
        RuntimeEnvironment.incSolverTime(endTime - startTime);
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
    public void computeGuidedSymAssumeOperationRequest(SymbolicOperation symbolicOperation) {
        long startTime = System.nanoTime();
        boolean concreteEval = symbolicOperation.concreteEvaluation();
        long endTime = System.nanoTime();
        RuntimeEnvironment.incSolverTime(endTime - startTime);
        if (concreteEval) {
            push(symbolicOperation);
        } else {
            boolean symbolicEval = solveSymbolicFormula(symbolicOperation);
            if (!symbolicEval) {
                System.out.println("[Incremental Symbolic Solver Message] : The guided sym assume is unsatisfiable");
                System.exit(0);
            }
            updateModel();
        }
    }

    /**
     * @param symbolicOperation
     */
    @Override
    public void computeNewSymbolicAssertOperationRequest(SymbolicOperation symbolicOperation) {
//        boolean concreteEval = symbolicOperation.concreteEvaluation();
//        if (concreteEval) {
//            RuntimeEnvironment.solverResult = true;
//        } else {
//            boolean sat = solveSymbolicFormula(symbolicOperation);
//            if (sat) {
//                RuntimeEnvironment.solverResult = true;
//                pop();
//            } else {
//                RuntimeEnvironment.solverResult = false;
//                push(negateFormula(symbolicOperation.getFormula()));
//            }
//        }
        boolean sat = solver(negateFormula(symbolicOperation.getFormula()));
        RuntimeEnvironment.solverResult = !sat;
        pop();
    }

    @Override
    public void computeGuidedSymbolicAssertOperationRequest(SymbolicOperation symbolicOperation) {
        // TODO: implement this method
    }

    private void updateModel() {
        if (model != null) {
            long startTime = System.nanoTime();
            model.iterator().forEachRemaining(entry -> {
                //System.out.println("[Incremental Symbolic Solver Message] : Model Entry: " + entry.getKey() + " : " + entry.getValue());
                // The key is a string like className@address. extract the class Name
                String symbolicType = entry.getKey().toString().split("@")[0];
                if (symbolicType.equals("SymbolicBoolean")) {
                    symBoolVariableMap.get(entry.getKey().toString()).setValue((Boolean) entry.getValue());
                } else if (symbolicType.equals("SymbolicInteger")) {
                    if (entry.getValue() instanceof BigInteger) {
                        symIntVariableMap.get(entry.getKey().toString()).setValue(((BigInteger) entry.getValue()).intValue());
                    } else {
                        symIntVariableMap.get(entry.getKey().toString()).setValue((Integer) entry.getValue());
                    }
                } else {
                    throw new RuntimeException("[Incremental Solver Message] Unknown Symbolic Type");
                }
            });
            long endTime = System.nanoTime();
            RuntimeEnvironment.incSolverTime(endTime - startTime);
        }
    }

    /**
     * @param symbolicOperation
     */
    @Override
    public void computeRandomlyNewSymbolicOperationRequest(SymbolicOperation symbolicOperation) {
        long startTime = System.nanoTime();
        boolean concreteEval = symbolicOperation.concreteEvaluation();
        long endTime = System.nanoTime();
        RuntimeEnvironment.incSolverTime(endTime - startTime);
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

    /**
     *
     */
    @Override
    public void solveAndUpdateModelSymbolicVariables() {
        if (prover.size() > 0) {
            try {
                long startTime = System.nanoTime();
                boolean isUnsat = prover.isUnsat();
                if (!isUnsat) {
                    model = prover.getModel();
                    long endTime = System.nanoTime();
                    RuntimeEnvironment.incSolverTime(endTime - startTime);
                    updateModel();
                } else {
                    //prover.getUnsatCore().forEach(System.out::println);
                    long endTime = System.nanoTime();
                    RuntimeEnvironment.incSolverTime(endTime - startTime);
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
    public void deprecatedComputeNewSymbolicOperationRequest(Thread thread, SymbolicOperation symbolicOperation) {
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
    public BooleanFormula pop() {
        long startTime = System.nanoTime();
        prover.pop();
        long endTime = System.nanoTime();
        RuntimeEnvironment.incSolverTime(endTime - startTime);
        //return stack.pop();
        return null;
    }

//    public void pop() {
//        prover.pop();
//    }

    @Override
    public void push() {
        try {
            long startTime = System.nanoTime();
            prover.push();
            long endTime = System.nanoTime();
            RuntimeEnvironment.incSolverTime(endTime - startTime);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void push(BooleanFormula formula) {
        try {
            long startTime = System.nanoTime();
            prover.push(formula);
            long endTime = System.nanoTime();
            RuntimeEnvironment.incSolverTime(endTime - startTime);
            //stack.push(formula);
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
            //System.out.println("[Incremental Symbolic Solver Message] : Solving the formula: " + formula.toString());
            long startTime = System.nanoTime();
            push(formula);
            boolean isUnsat = prover.isUnsat();
            if (!isUnsat) {
                model = prover.getModel();
                long endTime = System.nanoTime();
                RuntimeEnvironment.incSolverTime(endTime - startTime);
                //System.out.println("[Incremental Symbolic Solver Message] : The formula is satisfiable");
                return true;
            } else {
                //System.out.println("[Incremental Symbolic Solver Message] : The formula is unsatisfiable");
                long endTime = System.nanoTime();
                RuntimeEnvironment.incSolverTime(endTime - startTime);
                return false;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (SolverException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updatePathSymbolicOperations(SymbolicOperation symbolicOperation) {

    }

    @Override
    public ProverState createNewProver() {
        RuntimeEnvironment.maxProverId++;
        if (RuntimeEnvironment.proverPool.isEmpty()) {
            RuntimeEnvironment.numOfCreatedProvers++;
            long startTime = System.nanoTime();
            ProverEnvironment prover = context.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS);
            long endTime = System.nanoTime();
            RuntimeEnvironment.incSolverTime(endTime - startTime);
            return new ProverState(prover);
        } else {
            return RuntimeEnvironment.proverPool.remove(0);
        }
//        RuntimeEnvironment.numOfCreatedProvers++;
//        ProverEnvironment prover = context.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS);
//        return new ProverState(prover);
    }

    @Override
    public void setProver(ProverEnvironment prover, int proverId) {
        this.prover = prover;
        this.proverId = proverId;
    }

    /**
     * @param proverState
     * @param proverId
     */
    @Override
    public void setProver(ProverState proverState, int proverId) {
        this.prover = proverState.prover;
        this.proverId = proverId;
        this.symBoolVariableMap = proverState.symBoolVariableMap;
        this.symIntVariableMap = proverState.symIntVariableMap;
        this.symArrayVariableMap = proverState.symArrayVariableHashMap;
    }

    @Override
    public void setProver(ProverEnvironment prover, Stack<BooleanFormula> stack, int proverId) {
        this.prover = prover;
        //this.stack = stack;
        this.proverId = proverId;
    }

    @Override
    public int getProverId() {
        return proverId;
    }

    /**
     * @param prover
     */
    @Override
    public void resetProver(ProverEnvironment prover) {
        long startTime = System.nanoTime();
        while (prover.size() > 0) {
            prover.pop();
        }
        long endTime = System.nanoTime();
        RuntimeEnvironment.incSolverTime(endTime - startTime);
        //prover.close();
    }
}
