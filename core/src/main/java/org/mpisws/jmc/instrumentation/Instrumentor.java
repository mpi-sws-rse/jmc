package org.mpisws.jmc.instrumentation;

import net.bytebuddy.agent.ByteBuddyAgent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.jmc.checker.JmcTestTarget;

import java.lang.reflect.Method;

public class Instrumentor {
    static Logger LOGGER = LogManager.getLogger(Instrumentor.class);

    /**
     * Instruments the given test target. Assuming that the classes of the target are already loaded
     * into the class loader.
     *
     * @param target the test target to instrument
     * @return the instrumented target
     */
    public static InstrumentedTarget instrumentLoaded(JmcTestTarget target) {
        Class<?> targetClass = target.getClass();
        LOGGER.info("Instrumenting target: " + targetClass.getName());

        // TODO: continue from here. Use ByteBuddy or ASM to instrument the target.
        ByteBuddyAgent.install();
        try {
            Method invokeMethod = targetClass.getMethod("invoke");

        } catch (NoSuchMethodException e) {
            LOGGER.error("Method not found", e);
        }

        return new InstrumentedTarget(target);
    }
}
