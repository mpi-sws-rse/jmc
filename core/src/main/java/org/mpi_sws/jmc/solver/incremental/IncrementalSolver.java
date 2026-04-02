package org.mpi_sws.jmc.solver.incremental;


import org.mpi_sws.jmc.solver.ProverState;
import org.mpi_sws.jmc.solver.SMTSolverTypes;
import org.mpi_sws.jmc.solver.SolverResult;
import org.mpi_sws.jmc.solver.SymbolicSolver;
import org.mpi_sws.jmc.api.symbolic.array.SymArrayVariable;
import org.mpi_sws.jmc.api.symbolic.bool.JmcBooleanFormula;
import org.mpi_sws.jmc.api.symbolic.bool.SymBoolVariable;
import org.mpi_sws.jmc.api.symbolic.integer.SymIntVariable;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.api.SolverContext;
import org.sosy_lab.java_smt.api.SolverException;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class IncrementalSolver extends SymbolicSolver {

    public ProverEnvironment prover;
    private int proverId;
    private final Map<Integer, ProverState> proverMap = new HashMap<>();
    // Indicates the last logical prover id
    private int lastProverId = 0;
    // indicates the number of physical provers created
    private int numOfCreatedProvers = 0;
    // Holds the free provers
    private final ArrayList<ProverState> proverPool = new ArrayList<>();

    public IncrementalSolver() {
        super();
        ProverState proverState = createNewProver();
        proverMap.put(1, proverState);
        setProver(proverState, 1);
    }

    public IncrementalSolver(SMTSolverTypes solverType) {
        super(solverType);
        ProverState proverState = createNewProver();
        proverMap.put(1, proverState);
        setProver(proverState, 1);
    }

    @Override
    public int size() {
        return prover.size();
    }

    // TODO :: Put a check for the cases wher both SAT and UNSAT leads to contradiction and throw an exception
    @Override
    public SolverResult computeNewSymbolicOperation(JmcBooleanFormula symbolicFormula) {
        long startTime = System.nanoTime();
        boolean concreteEval = symbolicFormula.concreteEvaluation();
        long endTime = System.nanoTime();
        incSolverTime(endTime - startTime);
        boolean symbolicEval;
        if (concreteEval) {
            symbolicEval = disSolveSymbolicFormula(symbolicFormula);
        } else {
            symbolicEval = solveSymbolicFormula(symbolicFormula);
        }
        boolean bothSatUnsat = symbolicEval;
        pop();
        if (concreteEval) {
            push(symbolicFormula);
            // solver result is true
            return new SolverResult(true, bothSatUnsat);
        } else {
            push(negateFormula(symbolicFormula));
            // solver result is false
            return new SolverResult(false, bothSatUnsat);
        }
    }

    @Override
    public boolean computeNewSymAssumeOperation(JmcBooleanFormula symbolicOperation) {
        long startTime = System.nanoTime();
        boolean concreteEval = symbolicOperation.concreteEvaluation();
        long endTime = System.nanoTime();
        incSolverTime(endTime - startTime);
        if (concreteEval) {
            push(symbolicOperation);
            // solver result is true
            return true;
        } else {
            boolean symbolicEval = solveSymbolicFormula(symbolicOperation);
            if (symbolicEval) {
                updateModel();
            } else {
                pop();
            }
            // solver result is symbolicEval
            return symbolicEval;
        }
    }

    @Override
    public void computeGuidedSymAssumeOperation(JmcBooleanFormula symbolicOperation) {
        long startTime = System.nanoTime();
        boolean concreteEval = symbolicOperation.concreteEvaluation();
        long endTime = System.nanoTime();
        incSolverTime(endTime - startTime);
        if (concreteEval) {
            push(symbolicOperation);
        } else {
            boolean symbolicEval = solveSymbolicFormula(symbolicOperation);
            if (!symbolicEval) {
                throw new RuntimeException("Symbolic formula is unsatisfiable");
            }
            updateModel();
        }
    }

    @Override
    public boolean computeNewSymAssertOperation(JmcBooleanFormula symbolicOperation) {
        boolean sat = disSolveSymbolicFormula(symbolicOperation);
        pop();
        return !sat; // solver result is !sat
    }

    @Override
    public void solveAndUpdateModel() {
        if (prover.size() > 0) {
            try {
                long startTime = System.nanoTime();
                boolean isUnsat = prover.isUnsat();
                if (!isUnsat) {
                    model = prover.getModel();
                    long endTime = System.nanoTime();
                    incSolverTime(endTime - startTime);
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

    @Override
    protected void pop() {
        long startTime = System.nanoTime();
        prover.pop();
        long endTime = System.nanoTime();
        incSolverTime(endTime - startTime);
    }

    @Override
    protected void push() {
        try {
            long startTime = System.nanoTime();
            prover.push();
            long endTime = System.nanoTime();
            incSolverTime(endTime - startTime);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void push(org.sosy_lab.java_smt.api.BooleanFormula formula) {
        try {
            long startTime = System.nanoTime();
            prover.push(formula);
            long endTime = System.nanoTime();
            incSolverTime(endTime - startTime);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean solve(org.sosy_lab.java_smt.api.BooleanFormula formula) {
        try {
            long startTime = System.nanoTime();
            push(formula);
            boolean isUnsat = prover.isUnsat();
            if (!isUnsat) {
                model = prover.getModel();
                long endTime = System.nanoTime();
                incSolverTime(endTime - startTime);
                // The formula is satisfiable
                return true;
            } else {
                long endTime = System.nanoTime();
                incSolverTime(endTime - startTime);
                // The formula is unsatisfiable
                return false;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (SolverException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void push(JmcBooleanFormula operation) {
        push(operation.getFormula());
    }

    @Override
    public ProverState createNewProver() {
        this.lastProverId++;
        if (this.proverPool.isEmpty()) {
            this.numOfCreatedProvers++;
            long startTime = System.nanoTime();
            ProverEnvironment prover = context.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS);
            long endTime = System.nanoTime();
            incSolverTime(endTime - startTime);
            return new ProverState(prover);
        } else {
            return this.proverPool.remove(0);
        }
    }

    @Override
    public void setProver(ProverState proverState, int proverId) {
        this.prover = proverState.prover;
        this.proverId = proverId;
        this.symBoolVariableMap = proverState.symBoolVariableMap;
        this.symIntVariableMap = proverState.symIntVariableMap;
        this.symArrayVariableMap = proverState.symArrayVariableHashMap;
    }

    @Override
    public int getProverId() {
        return proverId;
    }

    @Override
    public void resetProver(ProverEnvironment prover) {
        long startTime = System.nanoTime();
        while (prover.size() > 0) {
            prover.pop();
        }
        long endTime = System.nanoTime();
        incSolverTime(endTime - startTime);
    }

    private void updateModel() {
        if (model != null) {
            long startTime = System.nanoTime();
            model.iterator().forEachRemaining(entry -> {
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
                    throw new RuntimeException("Unknown Symbolic Type");
                }
            });
            long endTime = System.nanoTime();
            incSolverTime(endTime - startTime);
        }
    }

    public void updateProver(int id) {
        if (id == 0) {
            throw new RuntimeException("Cannot update prover with zero id");
        }

        if (proverId != id) {
            ProverState p = proverMap.get(id);

            if (p == null) {
                throw new RuntimeException("Prover with id " + id + " does not exist");
            }
            setProver(p, id);
        }
    }

    public void restrictSolverStack(int levels) {
        while (levels > 0) {
            pop();
            levels--;
        }
    }

    public void updateWithCurrentProver(ProverState p) {
        for (Map.Entry<String, SymIntVariable> entry : symIntVariableMap.entrySet()) {
            p.symIntVariableMap.put(entry.getKey(), entry.getValue().clone());
        }

        for (Map.Entry<String, SymBoolVariable> entry : symBoolVariableMap.entrySet()) {
            p.symBoolVariableMap.put(entry.getKey(), entry.getValue().clone());
        }

        for (Map.Entry<String, SymArrayVariable> entry : symArrayVariableMap.entrySet()) {
            p.symArrayVariableHashMap.put(entry.getKey(), entry.getValue().clone());
        }
    }

    public int getLastProverId() {
        return lastProverId;
    }

    public void updateProverMap(int id, ProverState proverState) {
        proverMap.put(id, proverState);
    }

    public ProverState findProverState(int id) {
        return proverMap.get(id);
    }

    public void removeProver(int id) {
        ProverState p = proverMap.get(id);
        if (p == null) {
            throw new RuntimeException("Prover with id " + id + " does not exist");
        }

        // Remove prover from the map
        proverMap.remove(id);
        // Clear the prover stack
        resetProver(p.prover);
        // Clear prover's model
        p.clear();
        // Add prover to the pool
        proverPool.add(p);
    }
}
