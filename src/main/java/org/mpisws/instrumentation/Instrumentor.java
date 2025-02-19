package org.mpisws.instrumentation;

import net.bytebuddy.agent.ByteBuddyAgent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.checker.JmcTestTarget;

public class Instrumentor {
    static Logger LOGGER = LogManager.getLogger(Instrumentor.class);

    /**
     * Instruments the given test target.
     *
     * @param target the test target to instrument
     * @return the instrumented target
     */
    public static InstrumentedTarget instrument(JmcTestTarget target) {
        Class<?> targetClass = target.getClass();
        LOGGER.info("Instrumenting target: " + targetClass.getName());

        // TODO: continue from here. Use ByteBuddy or ASM to instrument the target.
        ByteBuddyAgent.install();

        return new InstrumentedTarget(target);
    }
}