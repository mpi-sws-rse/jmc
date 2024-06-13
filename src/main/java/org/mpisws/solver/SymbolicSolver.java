package org.mpisws.solver;

import org.mpisws.symbolic.*;
import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.java_smt.SolverContextFactory;
import org.sosy_lab.java_smt.api.*;
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SymbolicSolver {

    private Configuration config;
    private LogManager logger;
    private ShutdownManager shutdown;
    public Map<String, SymIntVariable> symIntVariableMap = new HashMap<>();
    public Map<String, SymBoolVariable> symBoolVariableMap = new HashMap<>();
    private SolverContext context;
    private FormulaManager fmgr;
    private BooleanFormulaManager bmgr;
    private IntegerFormulaManager imgr;
    private Model model;

    public SymbolicSolver() {
        try {
            config = Configuration.builder().build();
            logger = BasicLogManager.create(config);
            shutdown = ShutdownManager.create();
            context = SolverContextFactory.createSolverContext(
                    config, logger, shutdown.getNotifier(), SolverContextFactory.Solvers.SMTINTERPOL
            );
            fmgr = context.getFormulaManager();
            bmgr = fmgr.getBooleanFormulaManager();
            imgr = fmgr.getIntegerFormulaManager();
        } catch (InvalidConfigurationException e) {
            e.printStackTrace();
        }
    }

    public boolean solveSymbolicFormula(SymbolicOperation operation) {
        return solver(operation.getFormula());
    }

    public boolean disSolveSymbolicFormula(SymbolicOperation operation) {
        return solver(negateFormula(operation.getFormula()));
    }

    public boolean solveDependentSymbolicFormulas(SymbolicOperation operation, SymbolicOperation dependencyOperation) {
        BooleanFormula contextFormula = bmgr.and(operation.getFormula(), dependencyOperation.getFormula());
        return solver(contextFormula);
    }

    public boolean disSolveDependentSymbolicFormulas(SymbolicOperation operation, SymbolicOperation dependencyOperation) {
        BooleanFormula contextFormula = bmgr.and(negateFormula(operation.getFormula()), dependencyOperation.getFormula());
        return solver(contextFormula);
    }

    public BooleanFormula negateFormula(BooleanFormula formula) {
        return bmgr.not(formula);
    }

    public BooleanFormula makeDependencyFormula(List<BooleanFormula> formulas) {
        BooleanFormula dependencyFormula;
        if (formulas.size() == 1) {
            dependencyFormula = formulas.get(0);
        } else {
            dependencyFormula = formulas.get(0);
            for (int i = 1; i < formulas.size(); i++) {
                dependencyFormula = bmgr.and(dependencyFormula, formulas.get(i));
            }
        }
        return dependencyFormula;
    }

    public SymbolicOperation makeDependencyOperation(List<SymbolicOperation> operations) {
        SymbolicOperation dependencyOperation = new SymbolicOperation();
        BooleanFormula dependencyFormula;
        if (operations.size() == 1) {
            dependencyFormula = operations.get(0).getFormula();
        } else {
            dependencyFormula = operations.get(0).getFormula();
            for (int i = 1; i < operations.size(); i++) {
                dependencyFormula = bmgr.and(dependencyFormula, operations.get(i).getFormula());
                //dependencyOperation.getIntegerVariableMap().putAll(operations.get(i).getIntegerVariableMap());
                //dependencyOperation.getBooleanVariableMap().putAll(operations.get(i).getBooleanVariableMap());
            }
        }
        dependencyOperation.setFormula(dependencyFormula);
        return dependencyOperation;
    }

    private boolean solver(BooleanFormula formula) {
        try (ProverEnvironment prover = context.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS)) {
            System.out.println("Formula: " + formula);
            prover.addConstraint(formula);
            boolean isUnsat = prover.isUnsat();
            if (!isUnsat) {
                model = prover.getModel();
                evaluate();
                System.out.println("Satisfiable");
                prover.close();
                return true;
            } else {
                evaluate();
                System.out.println("Unsatisfiable");
                prover.close();
                return false;
            }
        } catch (SolverException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    private void evaluate() {

    }

    public Configuration getConfig() {
        return config;
    }

    public LogManager getLogger() {
        return logger;
    }

    public ShutdownManager getShutdown() {
        return shutdown;
    }

    public Map<String, SymIntVariable> getSymIntVariableMap() {
        return symIntVariableMap;
    }

    public SolverContext getContext() {
        return context;
    }

    public FormulaManager getFmgr() {
        return fmgr;
    }

    public BooleanFormulaManager getBmgr() {
        return bmgr;
    }

    public IntegerFormulaManager getImgr() {
        return imgr;
    }

    public Model getModel() {
        return model;
    }

    public SymIntVariable getSymIntVariable(String name) {
        if (symIntVariableMap.containsKey(name)) {
            return symIntVariableMap.get(name);
        } else {
            IntegerFormula symInt = imgr.makeVariable(name);
            SymIntVariable variable = new SymIntVariable(symInt);
            symIntVariableMap.put(name, variable);
            return variable;
        }
    }

    public SymBoolVariable getSymBoolVariable(String name) {
        if (symBoolVariableMap.containsKey(name)) {
            return symBoolVariableMap.get(name);
        } else {
            BooleanFormula symBool = bmgr.makeVariable(name);
            SymBoolVariable variable = new SymBoolVariable(symBool);
            symBoolVariableMap.put(name, variable);
            return variable;
        }
    }
}