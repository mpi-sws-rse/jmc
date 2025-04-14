package org.mpisws.jmc.test;

import org.junit.jupiter.api.Test;
import org.mpisws.jmc.checker.JmcCheckerConfiguration;
import org.mpisws.jmc.checker.JmcFunctionalTestTarget;
import org.mpisws.jmc.checker.JmcModelChecker;
import org.mpisws.jmc.checker.JmcTestTarget;
import org.mpisws.jmc.test.programs.CorrectCounterTestRunner;
import org.mpisws.jmc.test.programs.FutureCounterTestRunner;

/** The AgentIntegrationTest class is used to test the agent. */
public class AgentIntegrationTest {
    @Test
    public void testAgent() {
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
    public void testAgentWithFuture() {
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
}
