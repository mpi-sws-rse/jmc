package org.mpisws.jmc.instrumentation;

import org.junit.jupiter.api.Test;
import org.mpisws.jmc.checker.JmcFunctionalTestTarget;
import org.mpisws.jmc.checker.JmcTestTarget;
import org.mpisws.jmc.instrumentation.Instrumentor;

public class InstrumentationTest {
    @Test
    public void testInstrumentation() {
        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        "TestTarget",
                        () -> {
                            System.out.println("Invoking target");
                        });
        target = Instrumentor.instrumentLoaded(target);
        target.invoke();
    }
}
