package org.mpi_sws.jmc.solver.incremental;


import org.mpi_sws.jmc.solver.ProverState;
import org.mpi_sws.jmc.solver.SMTSolverTypes;
import org.mpi_sws.jmc.solver.SolverResult;
import org.mpi_sws.jmc.solver.SymbolicSolver;
import org.mpi_sws.jmc.api.symbolic.array.SymArrayVariable;
import org.mpi_sws.jmc.api.symbolic.bool.JmcBooleanFormula;
import org.mpi_sws.jmc.api.symbolic.bool.SymBoolVariable;
import org.mpi_sws.jmc.api.symbolic.integer.SymIntVariable;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.api.SolverContext;
import org.sosy_lab.java_smt.api.SolverException;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * The {@link SymbolicSolver} implementation JMC uses for ConDpor: an incremental solver over a
 * pooled set of prover contexts.
 *
 * <p>Two techniques make symbolic reasoning affordable during exploration:
 *
 * <ul>
 *   <li><b>Incremental solving.</b> Constraints are pushed and popped on the active prover's
 *       <em>stack</em> instead of being re-asserted from scratch, so extending the path condition (or
 *       rolling it back after a graph restrict, via {@link #restrictSolverStack(int)}) is cheap.
 *   <li><b>Prover pooling.</b> Each sub-exploration (backward revisit) needs its own solver state;
 *       creating JavaSMT provers is expensive, so freed provers are returned to {@link #proverPool}
 *       and reused. Logical prover ids map to {@link ProverState}s in {@link #proverMap}, and the
 *       exploration switches the active context with {@link #setProverById(int)}.
 * </ul>
 *
 * <p>The concolic optimization lives in {@link #computeNewSymbolicOperation}: the taken branch is
 * evaluated against the concrete model with no solver call, and only the other branch is queried.
 */
public class IncrementalSolver extends SymbolicSolver {

    /** The active prover's JavaSMT environment (mirrors the current {@link ProverState}). */
    public ProverEnvironment prover;
    /** The logical id of the active prover. */
    private int proverId;
    /** Logical prover id to its state. */
    private final Map<Integer, ProverState> proverMap = new HashMap<>();
    /** The last logical prover id handed out. */
    private int lastProverId = 0;
    /** The number of physical provers actually created (pool misses). */
    private int numOfCreatedProvers = 0;
    /** Freed prover states available for reuse. */
    private final ArrayList<ProverState> proverPool = new ArrayList<>();

    /** Creates an incremental solver with the default backend and an initial prover (id 1). */
    public IncrementalSolver() {
        super();
        ProverState proverState = createNewProver();
        proverMap.put(1, proverState);
        setProver(proverState, 1);
    }

    /**
     * Creates an incremental solver with the given backend and an initial prover (id 1).
     *
     * @param solverType the SMT backend to use.
     */
    public IncrementalSolver(SMTSolverTypes solverType) {
        super(solverType);
        ProverState proverState = createNewProver();
        proverMap.put(1, proverState);
        setProver(proverState, 1);
    }

    /**
     * Returns the number of constraints on the active prover's stack.
     *
     * @return the stack depth.
     */
    @Override
    public int size() {
        return prover.size();
    }

    /**
     * Evaluates a constraint-evaluation event (the concolic core).
     *
     * <p>The branch taken now is decided by {@link JmcBooleanFormula#concreteEvaluation()} against
     * the current model — no solver call. Only the <em>other</em> branch is queried, to learn whether
     * it is also satisfiable ({@code isNegatable}). The prover stack is left holding the constraint
     * for the taken branch.
     *
     * @param symbolicFormula the constraint being evaluated.
     * @return the branch outcome and whether the opposite branch is feasible.
     */
    // TODO :: Put a check for the cases wher both SAT and UNSAT leads to contradiction and throw an exception
    @Override
    public SolverResult computeNewSymbolicOperation(JmcBooleanFormula symbolicFormula) {
        long startTime = System.nanoTime();
        boolean concreteEval = symbolicFormula.concreteEvaluation();
        long endTime = System.nanoTime();
        advanceSolverTime(endTime - startTime);
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

    /**
     * Evaluates a symbolic {@code assume}: if the concrete branch holds, asserts it; otherwise
     * queries the solver and either refreshes the model (satisfiable) or rolls back (unsatisfiable).
     *
     * @param symbolicOperation the assumed constraint.
     * @return whether the assumption holds.
     */
    @Override
    public boolean computeNewSymAssumeOperation(JmcBooleanFormula symbolicOperation) {
        long startTime = System.nanoTime();
        boolean concreteEval = symbolicOperation.concreteEvaluation();
        long endTime = System.nanoTime();
        advanceSolverTime(endTime - startTime);
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

    /**
     * Replays a symbolic {@code assume} during guided re-execution: asserts the constraint and
     * refreshes the model, throwing if it is (unexpectedly) unsatisfiable.
     *
     * @param symbolicOperation the assumed constraint.
     */
    @Override
    public void computeGuidedSymAssumeOperation(JmcBooleanFormula symbolicOperation) {
        long startTime = System.nanoTime();
        boolean concreteEval = symbolicOperation.concreteEvaluation();
        long endTime = System.nanoTime();
        advanceSolverTime(endTime - startTime);
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

    /**
     * Evaluates a symbolic {@code assert}: the property necessarily holds iff its negation is
     * unsatisfiable. The temporary negation is popped before returning.
     *
     * @param symbolicOperation the asserted constraint.
     * @return true if the assertion cannot be violated.
     */
    @Override
    public boolean computeNewSymAssertOperation(JmcBooleanFormula symbolicOperation) {
        boolean sat = disSolveSymbolicFormula(symbolicOperation);
        pop();
        return !sat; // solver result is !sat
    }

    /**
     * Solves the current constraint stack and refreshes the concrete model, throwing if it is
     * unsatisfiable.
     */
    @Override
    public void solveAndUpdateModel() {
        if (prover.size() > 0) {
            try {
                long startTime = System.nanoTime();
                boolean isUnsat = prover.isUnsat();
                if (!isUnsat) {
                    model = prover.getModel();
                    long endTime = System.nanoTime();
                    advanceSolverTime(endTime - startTime);
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

    /** Pops the top level off the active prover's stack. */
    @Override
    public void pop() {
        long startTime = System.nanoTime();
        prover.pop();
        long endTime = System.nanoTime();
        advanceSolverTime(endTime - startTime);
    }

    /** Pushes an empty backtracking level onto the active prover's stack. */
    @Override
    protected void push() {
        try {
            long startTime = System.nanoTime();
            prover.push();
            long endTime = System.nanoTime();
            advanceSolverTime(endTime - startTime);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Pushes a JavaSMT boolean formula onto the active prover's stack.
     *
     * @param formula the formula to assert.
     */
    @Override
    protected void push(BooleanFormula formula) {
        try {
            long startTime = System.nanoTime();
            prover.push(formula);
            long endTime = System.nanoTime();
            advanceSolverTime(endTime - startTime);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Pushes the formula and checks satisfiability, caching the model when satisfiable.
     *
     * @param formula the formula to solve.
     * @return true if satisfiable, false otherwise.
     */
    @Override
    protected boolean solve(BooleanFormula formula) {
        try {
            long startTime = System.nanoTime();
            push(formula);
            boolean isUnsat = prover.isUnsat();
            if (!isUnsat) {
                model = prover.getModel();
                long endTime = System.nanoTime();
                advanceSolverTime(endTime - startTime);
                // The formula is satisfiable
                return true;
            } else {
                long endTime = System.nanoTime();
                advanceSolverTime(endTime - startTime);
                // The formula is unsatisfiable
                return false;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (SolverException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Pushes a symbolic constraint onto the active prover's stack.
     *
     * @param operation the constraint to assert.
     */
    @Override
    protected void push(JmcBooleanFormula operation) {
        push(operation.getFormula());
    }

    /**
     * Returns a prover context: a free one from the pool if available, otherwise a freshly created
     * JavaSMT prover (with model generation enabled).
     *
     * @return the prover state.
     */
    @Override
    public ProverState createNewProver() {
        this.lastProverId++;
        if (this.proverPool.isEmpty()) {
            this.numOfCreatedProvers++;
            long startTime = System.nanoTime();
            ProverEnvironment prover = context.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS);
            long endTime = System.nanoTime();
            advanceSolverTime(endTime - startTime);
            return new ProverState(prover);
        } else {
            return this.proverPool.remove(0);
        }
    }

    /**
     * Activates the given prover state under the given logical id, switching the active prover and
     * its symbolic-variable maps.
     *
     * @param proverState the prover state to activate.
     * @param proverId the logical prover id.
     */
    @Override
    public void setProver(ProverState proverState, int proverId) {
        this.prover = proverState.prover;
        this.proverId = proverId;
        this.symBoolVariableMap = proverState.symBoolVariableMap;
        this.symIntVariableMap = proverState.symIntVariableMap;
        this.symArrayVariableMap = proverState.symArrayVariableHashMap;
    }

    /**
     * Returns the logical id of the active prover.
     *
     * @return the active prover id.
     */
    @Override
    public int getProverId() {
        return proverId;
    }

    /**
     * Empties the given prover's constraint stack.
     *
     * @param prover the prover to reset.
     */
    @Override
    public void resetProver(ProverEnvironment prover) {
        long startTime = System.nanoTime();
        while (prover.size() > 0) {
            prover.pop();
        }
        long endTime = System.nanoTime();
        advanceSolverTime(endTime - startTime);
    }

    /** Empties the active prover's constraint stack. */
    @Override
    public void resetCurrentProver() {
        resetProver(this.prover);
    }

    /**
     * Reads the current SMT model back into the symbolic variables, concretizing each symbolic
     * boolean/integer to its model value — the concrete witness each symbolic value carries for
     * concolic evaluation.
     */
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
            advanceSolverTime(endTime - startTime);
        }
    }

    /**
     * Switches the active prover to the one with the given logical id (a no-op if already active).
     *
     * @param id the logical prover id.
     * @throws RuntimeException if the id is negative or unknown.
     */
    public void setProverById(int id) {
        if (id < 0) {
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

    /**
     * Pops the given number of levels off the active prover's stack — used to roll the path condition
     * back when a graph restrict removes that many symbolic events.
     *
     * @param levels the number of constraints to pop.
     */
    public void restrictSolverStack(int levels) {
        while (levels > 0) {
            pop();
            levels--;
        }
    }

    /**
     * Clones the current prover state into the given prover state. This is useful when we want to create a new prover
     * state that is identical to the current prover state, but with a different prover environment.
     * @param p the prover state to clone into
     */
    public void cloneCurrentProverState(ProverState p) {
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

    /**
     * Returns the last logical prover id handed out.
     *
     * @return the last prover id.
     */
    public int getLastProverId() {
        return lastProverId;
    }


    /**
     * Registers a prover state under a fresh logical id and returns that id.
     *
     * @param prover the prover state to register.
     * @return the assigned logical id.
     */
    public int registerNewProver(ProverState prover) {
        int newProverId = getLastProverId();
        updateProverMap(newProverId, prover);
        return newProverId;
    }

    /**
     * Records a prover state under the given logical id.
     *
     * @param id the logical id.
     * @param proverState the prover state.
     */
    public void updateProverMap(int id, ProverState proverState) {
        proverMap.put(id, proverState);
    }

    /**
     * Returns the prover state for the given logical id, or {@code null} if unknown.
     *
     * @param id the logical id.
     * @return the prover state, or {@code null}.
     */
    public ProverState findProverState(int id) {
        return proverMap.get(id);
    }

    /**
     * Retires the prover with the given id: unregisters it, clears its stack and variables, and
     * returns it to the pool for reuse.
     *
     * @param id the logical prover id to remove.
     * @throws RuntimeException if the id is unknown.
     */
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
