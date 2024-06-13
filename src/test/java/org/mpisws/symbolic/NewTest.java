package org.mpisws.symbolic;

import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.java_smt.SolverContextFactory;
import org.sosy_lab.java_smt.api.*;

import java.math.BigInteger;

public class NewTest {
    public static void main(String[] args) throws InvalidConfigurationException, InterruptedException, SolverException {
        Configuration config = Configuration.builder().build();
        LogManager logger = BasicLogManager.create(config);
        ShutdownManager shutdown = ShutdownManager.create();

        // SolverContext is a class wrapping a solver context.
        // Solver can be selected either using an argument or a configuration option
        // inside `config`.
        SolverContext context = SolverContextFactory.createSolverContext(
                config, logger, shutdown.getNotifier(), SolverContextFactory.Solvers.SMTINTERPOL);

        // Assume we have a SolverContext instance.
        FormulaManager fmgr = context.getFormulaManager();

        BooleanFormulaManager bmgr = fmgr.getBooleanFormulaManager();
        IntegerFormulaManager imgr = fmgr.getIntegerFormulaManager();

        NumeralFormula.IntegerFormula a = imgr.makeVariable("a"),
                b = imgr.makeVariable("b"),
                c = imgr.makeVariable("c");
        BooleanFormula constraint = bmgr.or(
                imgr.greaterThan(
                        imgr.add(a, b), c
                ),
                imgr.lessThan(
                        imgr.add(a, c), imgr.multiply(imgr.makeNumber(2), b)
                )
        );
        System.out.println(constraint);


        Model model = null;
        try (ProverEnvironment prover = context.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS)) {
            prover.addConstraint(constraint);
            boolean isUnsat = prover.isUnsat();
            if (!isUnsat) {
                model = prover.getModel();
            }
        }

        BigInteger value = model.evaluate(a);
        BigInteger value2 = model.evaluate(b);
        BigInteger value3 = model.evaluate(c);

        System.out.println(value);
        System.out.println(value2);
        System.out.println(value3);


    }

}
