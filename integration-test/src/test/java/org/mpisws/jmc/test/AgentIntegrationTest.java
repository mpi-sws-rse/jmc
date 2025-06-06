package org.mpisws.jmc.test;

import org.junit.jupiter.api.Test;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.checker.JmcCheckerConfiguration;
import org.mpisws.jmc.checker.JmcFunctionalTestTarget;
import org.mpisws.jmc.checker.JmcModelChecker;
import org.mpisws.jmc.checker.JmcTestTarget;
import org.mpisws.jmc.checker.exceptions.JmcCheckerException;
import org.mpisws.jmc.test.atomic.counter.AtomicCounter;
import org.mpisws.jmc.test.bigShot.BigShotP;
import org.mpisws.jmc.test.bigShot.BigShotS;
import org.mpisws.jmc.test.bigShot.BigShotSII;
import org.mpisws.jmc.test.concrete.gcd.ParallelGCD;
import org.mpisws.jmc.test.det.array.DetArray;
import org.mpisws.jmc.test.programs.CorrectCounterTestRunner;
import org.mpisws.jmc.test.programs.FutureCounterTestRunner;
import org.mpisws.jmc.test.assume.SendRecv;

/**
 * The AgentIntegrationTest class is used to test the agent.
 */
public class AgentIntegrationTest {
    @Test
    public void testAgent() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).debug(true).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomBuggyCounter",
                        () -> {
                            CorrectCounterTestRunner.main(new String[0]);
                        });
        jmcModelChecker.check(target);
    }

    @Test
    public void testAgentWithFuture() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).debug(true).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomFutureCounter",
                        () -> {
                            FutureCounterTestRunner.main(new String[0]);
                        });
        jmcModelChecker.check(target);
    }

    @Test
    public void testAssume() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .strategyType("trust")
                        .numIterations(10)
                        .debug(false)
                        .build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "AssumeTest",
                        () -> {
                            SendRecv.main(new String[0]);
                        });
        jmcModelChecker.check(target);
    }

    @Test
    public void testAtomicCounter() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .strategyType("trust")
                        .numIterations(100000)
                        .debug(false)
                        .build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "AtomicCounter",
                        () -> {
                            int length = 8; // Default length
                            AtomicCounter.main(new String[]{String.valueOf(length)});
                        });
        jmcModelChecker.check(target);
    }

    @Test
    public void testBigShotP() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .strategyType("trust")
                        .numIterations(1000)
                        .debug(false)
                        .build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "BigShotPTest",
                        () -> {
                            BigShotP.main(new String[0]);
                        });
        jmcModelChecker.check(target);
    }

    @Test
    public void testBigShotS() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .strategyType("trust")
                        .numIterations(1000)
                        .debug(false)
                        .build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "BigShotSTest",
                        () -> {
                            BigShotS.main(new String[0]);
                        });
        jmcModelChecker.check(target);
    }

    @Test
    public void testDetArray() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .strategyType("trust")
                        .numIterations(1111000)
                        .debug(false)
                        .build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "DetArrayTest",
                        () -> {
                            String[] args = {"5"}; // Example argument
                            DetArray.main(args);
                        });
        jmcModelChecker.check(target);
    }

    @Test
    public void testBigShotSII() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .strategyType("trust")
                        .numIterations(1000)
                        .debug(false)
                        .build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "BigShotSII",
                        () -> {
                            BigShotSII.main(new String[0]);
                        });
        jmcModelChecker.check(target);
    }

    @Test
    public void testParallelGCD() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder()
                        .strategyType("trust")
                        .numIterations(1000)
                        .debug(false)
                        .build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "ParallelGCD",
                        () -> {
                            // Assuming ParallelGCD is a class with a main method
                            // that runs the GCD algorithm in parallel.
                            int a = 5; // Example value
                            int b = 5; // Example value
                            ParallelGCD.main(new String[]{String.valueOf(a), String.valueOf(b)});
                        });
        jmcModelChecker.check(target);
    }

    @JmcCheckConfiguration(numIterations = 10, strategy = "random", debug = true)
    @Test
    public void testAgentWithFutureAgain() {
        CorrectCounterTestRunner.main(new String[0]);
    }
}
