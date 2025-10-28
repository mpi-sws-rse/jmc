package org.mpi_sws.jmc.test;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.checker.JmcTestTarget;
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException;
import org.mpi_sws.jmc.test.programs.*;

/** The AgentIntegrationTest class is used to test the agent. */
public class AgentIntegrationTest {

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
    public void testAgentWithFutureTask() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).debug(false).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomFutureTaskCounter",
                        () -> {
                            FutureTaskCounterTestRunner.main(new String[0]);
                        });
        jmcModelChecker.check(target);
    }

    @Test
    public void testAgentWithExecutor() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).debug(true).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomExecutorCounter",
                        () -> {
                            ExecutorTestRunner.main(new String[0]);
                        });
        jmcModelChecker.check(target);
    }

    @Test
    public void testAgentWithGuavaMoreExecutor() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).debug(true).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomGuavaMoreExecutorCounter",
                        () -> {
                            GuavaCounterTestRunner.main(new String[0]);
                        });
        jmcModelChecker.check(target);
    }

    @Test
    public void testAgentWithFutureList() throws JmcCheckerException {
        JmcCheckerConfiguration config =
                new JmcCheckerConfiguration.Builder().numIterations(10).debug(true).build();
        JmcModelChecker jmcModelChecker = new JmcModelChecker(config);

        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "RandomFuturesList",
                        () -> {
                            FutureCounterListTest.main(new String[0]);
                        });
        jmcModelChecker.check(target);
    }
}
