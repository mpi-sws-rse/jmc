package org.mpisws.instrumentation;

import org.junit.jupiter.api.Test;
import org.mpisws.checker.JmcFunctionalTestTarget;
import org.mpisws.checker.JmcTestTarget;

public class InstrumentationTest {
    @Test
    public void testInstrumentation() {
        JmcTestTarget target = new JmcFunctionalTestTarget("TestTarget", () -> {
                System.out.println("Invoking target");
            }
        );
        target = Instrumentor.instrument(target);
        target.invoke();
    }
}
