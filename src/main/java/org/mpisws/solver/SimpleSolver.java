package org.mpisws.solver;
//
// import org.apache.logging.log4j.LogManager;
// import org.apache.logging.log4j.Logger;
// import org.mpisws.runtime.JmcRuntime;
// import org.mpisws.symbolic.SymbolicOperation;
// import org.sosy_lab.java_smt.api.BooleanFormula;
// import org.sosy_lab.java_smt.api.ProverEnvironment;
// import org.sosy_lab.java_smt.api.SolverContext;
// import org.sosy_lab.java_smt.api.SolverException;
//
// import java.util.ArrayList;
// import java.util.List;
//
// public class SimpleSolver extends SymbolicSolver {
//
//    private static final Logger LOGGER = LogManager.getLogger(SimpleSolver.class);
//
//    public SimpleSolver() {
//        super();
//    }
//
//    public SimpleSolver(SMTSolverTypes type) {
//        super(type);
//    }
//
//    /**
//     * @param symbolicOperation
//     */
//    @Override
//    public void computeNewSymbolicOperationRequest(SymbolicOperation symbolicOperation) {
//        List<SymbolicOperation> dependentOperations = findDependentFormulas(symbolicOperation);
//        if (dependentOperations == null) {
//            handleFreeFormulas(symbolicOperation);
//        } else {
//            handleDependentFormulas(symbolicOperation, dependentOperations);
//        }
//    }
//
//    /**
//     * @param symbolicOperation
//     */
//    @Override
//    public void computeNewSymAssumeOperationRequest(SymbolicOperation symbolicOperation) {
//        List<SymbolicOperation> dependentOperations = findDependentFormulas(symbolicOperation);
//        if (dependentOperations == null) {
//            JmcRuntime.solverResult = solveSymbolicFormula(symbolicOperation);
//        } else {
//            SymbolicOperation dependency = makeDependencyOperation(dependentOperations);
//            JmcRuntime.solverResult =
//                    solveDependentSymbolicFormulas(symbolicOperation, dependency);
//        }
//    }
//
//    /**
//     * @param symbolicOperation
//     */
//    @Override
//    public void computeSymbolicAssertOperationRequest(SymbolicOperation symbolicOperation) {
//        List<SymbolicOperation> dependentOperations = findDependentFormulas(symbolicOperation);
//        if (dependentOperations == null) {
//            JmcRuntime.solverResult = solveSymbolicFormula(symbolicOperation);
//        } else {
//            SymbolicOperation dependency = makeDependencyOperation(dependentOperations);
//            JmcRuntime.solverResult =
//                    solveDependentSymbolicFormulas(symbolicOperation, dependency);
//        }
//
//        if (!JmcRuntime.solverResult) {
//            updatePathSymbolicOperationsWithNegate(symbolicOperation);
//        }
//    }
//
//    /**
//     * @param symbolicOperation
//     */
//    @Override
//    public void computeRandomlyNewSymbolicOperationRequest(SymbolicOperation symbolicOperation) {
//        computeNewSymbolicOperationRequest(symbolicOperation);
//    }
//
//    /**
//     * Handles the free formulas.
//     *
//     * <p>This method handles the free formulas. It calls the {@link
//     * #solveSymbolicFormula(SymbolicOperation)} and {@link
//     * #disSolveSymbolicFormula(SymbolicOperation)} methods to solve the symbolic operation. Then,
//     * it calls the {@link #pickSatOrUnsat(boolean, boolean)} method to pick the result of the
//     * symbolic operation. Finally, it sets the {@link JmcRuntime#solverResult} with the
//     * result of the symbolic operation.
//     *
//     * @param symbolicOperation is the symbolic operation that is going to be solved.
//     */
//    private void handleFreeFormulas(SymbolicOperation symbolicOperation) {
//        LOGGER.debug("The symbolic arithmetic operation is free from dependencies");
//        boolean sat = solveSymbolicFormula(symbolicOperation);
//        boolean unSat = disSolveSymbolicFormula(symbolicOperation);
//        bothSatUnsat = sat && unSat;
//        JmcRuntime.solverResult = pickSatOrUnsat(sat, unSat);
//    }
//
//    /**
//     * Finds the dependent formulas of the given symbolic operation.
//     *
//     * <p>This method finds the dependent formulas of the given symbolic operation. It iterates
// over
//     * the path symbolic operations and checks whether the given symbolic operation is dependent
// on
//     * another symbolic operation. If it is dependent, it adds the dependent symbolic operation to
//     * the list of dependent formulas. Otherwise, it returns null.
//     *
//     * @param symbolicOperation is the symbolic operation that is going to be checked.
//     * @return the list of dependent formulas of the given symbolic operation.
//     */
//    private List<SymbolicOperation> findDependentFormulas(SymbolicOperation symbolicOperation) {
//        List<SymbolicOperation> dependencyOperations = new ArrayList<>();
//        List<SymbolicOperation> symbolicOperations = JmcRuntime.pathSymbolicOperations;
//        for (SymbolicOperation symOp : symbolicOperations) {
//            if (symOp.isFormulaDependent(symbolicOperation)) {
//                LOGGER.debug(
//                        "The symbolic arithmetic operation is dependent on "
//                                + "another symbolic arithmetic operation");
//                LOGGER.debug(
//                        "The dependent symbolic arithmetic operations are : {} and {}",
//                        symOp.getFormula().toString(),
//                        symbolicOperation.getFormula().toString());
//                dependencyOperations.add(symOp);
//            }
//        }
//        if (dependencyOperations.isEmpty()) {
//            return null;
//        } else {
//            return dependencyOperations;
//        }
//    }
//
//    /**
//     * Finds the dependent formulas of the given symbolic operation based on the thread.
//     *
//     * <p>This method finds the dependent formulas of the given symbolic operation based on the
//     * thread. It iterates over the symbolic operations of the thread and checks whether the given
//     * symbolic operation is dependent on another symbolic operation. If it is dependent, it adds
//     * the dependent symbolic operation to the list of dependent formulas. Otherwise, it returns
//     * null.
//     *
//     * @param thread is the thread that is going to be checked.
//     * @param symbolicOperation is the symbolic operation that is going to be checked.
//     * @return the list of dependent formulas of the given symbolic operation.
//     */
//    private List<SymbolicOperation> findDependentThreadFormulas(
//            Thread thread, SymbolicOperation symbolicOperation) {
//        List<SymbolicOperation> dependencyOperations = new ArrayList<>();
//        List<SymbolicOperation> symbolicOperations =
//                JmcRuntime.threadSymbolicOperation.get(
//                        JmcRuntime.threadManager.getRevId(thread.getId()));
//        for (SymbolicOperation symOp : symbolicOperations) {
//            if (symOp.isFormulaDependent(symbolicOperation)) {
//                dependencyOperations.add(symOp);
//            }
//        }
//        if (dependencyOperations.isEmpty()) {
//            return null;
//        } else {
//            return dependencyOperations;
//        }
//    }
//
//    /**
//     * Handles the dependent formulas.
//     *
//     * <p>This method handles the dependent formulas. First, it creates a dependency operation
// from
//     * the list of dependent formulas. Then, it calls the {@link
//     * #solveDependentSymbolicFormulas(SymbolicOperation, SymbolicOperation)} and {@link
//     * #disSolveDependentSymbolicFormulas(SymbolicOperation, SymbolicOperation)} methods to solve
//     * the symbolic operation. Then, it calls the {@link #pickSatOrUnsat(boolean, boolean)} method
//     * to pick the result of the symbolic operation. Finally, it sets the {@link
//     * JmcRuntime#solverResult} with the result of the symbolic operation.
//     *
//     * @param symbolicOperation is the symbolic operation that is going to be solved.
//     * @param dependentOperations is the list of dependent formulas of the symbolic operation.
//     */
//    private void handleDependentFormulas(
//            SymbolicOperation symbolicOperation, List<SymbolicOperation> dependentOperations) {
//        LOGGER.debug("The symbolic arithmetic operation has dependencies");
//        SymbolicOperation dependency = makeDependencyOperation(dependentOperations);
//        boolean sat = solveDependentSymbolicFormulas(symbolicOperation, dependency);
//        boolean unSat = disSolveDependentSymbolicFormulas(symbolicOperation, dependency);
//        bothSatUnsat = sat && unSat;
//        JmcRuntime.solverResult = pickSatOrUnsat(sat, unSat);
//    }
//
//    @Override
//    protected boolean solver(BooleanFormula formula) {
//        try (ProverEnvironment prover =
//                context.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS)) {
//            LOGGER.debug("Solving the formula: {}", formula.toString());
//            prover.addConstraint(formula);
//            boolean isUnsat = prover.isUnsat();
//            if (!isUnsat) {
//                model = prover.getModel();
//                evaluate();
//                LOGGER.debug("The formula is satisfiable");
//                prover.close();
//                return true;
//            } else {
//                evaluate();
//                LOGGER.debug("The formula is unsatisfiable");
//                prover.close();
//                return false;
//            }
//        } catch (SolverException e) {
//            e.printStackTrace();
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//        return false;
//    }
//
//    private void evaluate() {
//        // TODO() : TO BE COMPLETED
//    }
//
//    @Override
//    public void updatePathSymbolicOperations(SymbolicOperation symbolicOperation) {
//        if (JmcRuntime.solverResult) {
//            addToPathSymbolicOperations(symbolicOperation);
//        } else {
//            updatePathSymbolicOperationsWithNegate(symbolicOperation);
//        }
//    }
//
//    /** */
//    @Override
//    public void solveAndUpdateModelSymbolicVariables() {}
//
//    private void addToPathSymbolicOperations(SymbolicOperation symbolicOperation) {
//        JmcRuntime.pathSymbolicOperations.add(symbolicOperation);
//    }
//
//    private void updatePathSymbolicOperationsWithNegate(SymbolicOperation symbolicOperation) {
//        addToPathSymbolicOperations(negateSymbolicOperation(symbolicOperation));
//    }
//
//    /**
//     * Negates the given symbolic operation.
//     *
//     * <p>It uses the solver to negate the formula of the symbolic operation.
//     *
//     * @param symbolicOperation is the symbolic operation that is going to be negated.
//     * @return the negated symbolic operation.
//     */
//    private SymbolicOperation negateSymbolicOperation(SymbolicOperation symbolicOperation) {
//        symbolicOperation.setFormula(
//                JmcRuntime.solver.negateFormula(symbolicOperation.getFormula()));
//        LOGGER.debug("The negated formula is saved {}", symbolicOperation.getFormula());
//        return symbolicOperation;
//    }
// }
