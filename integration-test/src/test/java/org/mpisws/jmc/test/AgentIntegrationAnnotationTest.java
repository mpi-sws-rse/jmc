package org.mpisws.jmc.test;

import org.junit.jupiter.api.Test;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.test.programs.CorrectCounterTestRunner;

@JmcCheckConfiguration(numIterations = 10, strategy = "random", debug = true)
public class AgentIntegrationAnnotationTest {
    @Test
    public void testAgentWithFutureAgain() {
        CorrectCounterTestRunner.main(new String[0]);
    }
}
