package org.mpisws.jmc.test;

import org.junit.jupiter.api.Test;
import org.mpisws.jmc.test.programs.CorrectCounterITest;
import org.mpisws.jmc.test.programs.CorrectCounterTestRunner;
import org.mpisws.jmc.test.programs.CounterITest;

import java.util.concurrent.locks.ReentrantLock;

/** The AgentIntegrationTest class is used to test the agent. */
public class AgentIntegrationTest {
    @Test
    public void testAgent() {
        CorrectCounterTestRunner.main(new String[] {});
    }
}
