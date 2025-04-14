package org.mpisws.jmc.agent;

import org.junit.jupiter.api.Test;
import org.mpisws.jmc.agent.programs.CorrectCounter;

/** The AgentIntegrationTest class is used to test the agent. */
public class AgentIntegrationTest {
    @Test
    public void testAgent() {
        CorrectCounter.main(new String[0]);
    }
}
