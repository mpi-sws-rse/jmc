package org.mpisws.jmc.agent.test;

import org.junit.jupiter.api.Test;
import org.mpisws.jmc.agent.visitors.JmcThreadVisitor;

public class AgentThreadInstrumentationTest {

    @Test
    public void testThreadWrapper() {
        String originalTestClass =
                "src/test/java/org/mpisws/jmc/agent/test/programs/OriginalTestThread.class";
        String expectedTestClass =
                "src/test/java/org/mpisws/jmc/agent/test/programs/ExpectedTestThread.class";

        try {
            AgentTestUtil.check(
                    originalTestClass,
                    expectedTestClass,
                    cw ->
                            new JmcThreadVisitor.ThreadClassWrapperVisitor(
                                    new JmcThreadVisitor.ThreadCallReplacerClassVisitor(cw)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to check class", e);
        }
    }
}
