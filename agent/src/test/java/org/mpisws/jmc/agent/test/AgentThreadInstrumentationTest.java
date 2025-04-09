package org.mpisws.jmc.agent.test;

import org.junit.jupiter.api.Test;
import org.mpisws.jmc.agent.visitors.JmcFutureVisitor;
import org.mpisws.jmc.agent.visitors.JmcThreadVisitor;

public class AgentThreadInstrumentationTest {

    @Test
    public void testThreadWrapper() {
        String originalTestClass =
                "src/test/java/org/mpisws/jmc/agent/test/programs/FutureCounter.class";
        String expectedTestClass =
                "src/test/java/org/mpisws/jmc/agent/test/programs/ExpectedFutureCounter.class";

        try {
            AgentTestUtil.translateAndStore(
                    originalTestClass,
                    expectedTestClass,
                    cw ->
                            new JmcFutureVisitor.JmcExecutorsClassVisitor(
                                    new JmcThreadVisitor.ThreadCallReplacerClassVisitor(cw)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to check class", e);
        }
    }
}
