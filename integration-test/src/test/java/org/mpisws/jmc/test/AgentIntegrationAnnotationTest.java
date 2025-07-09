package org.mpisws.jmc.test;

import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcReplay;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpisws.jmc.test.programs.CorrectCounterTestRunner;

@JmcCheckConfiguration(numIterations = 10)
public class AgentIntegrationAnnotationTest {

    @JmcCheck
    @JmcTrustStrategy(debug = true)
    public void testAgentWithFutureAgain() {
        CorrectCounterTestRunner.main(new String[0]);
    }
}
