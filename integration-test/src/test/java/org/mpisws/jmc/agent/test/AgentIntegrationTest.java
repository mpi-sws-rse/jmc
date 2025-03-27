package org.mpisws.jmc.agent.test;

import org.junit.jupiter.api.Test;
import org.mpisws.jmc.agent.test.programs.CorrectCounterITest;

/** The AgentIntegrationTest class is used to test the agent. */
public class AgentIntegrationTest {
    @Test
    public void testAgent() {
        CorrectCounterITest.main(new String[0]);
    }
}
