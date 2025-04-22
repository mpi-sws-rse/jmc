package org.mpisws.jmc.integrations.junit5.engine;

import org.mpisws.jmc.checker.JmcCheckerConfiguration;
import org.mpisws.jmc.checker.JmcFunctionalTestTarget;
import org.mpisws.jmc.checker.JmcModelChecker;
import org.mpisws.jmc.checker.JmcTestTarget;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class JmcTestExecutor {

    public static void execute(Method testMethod, Object instance, JmcCheckerConfiguration config) {
        JmcModelChecker checker = new JmcModelChecker(config);
        JmcTestTarget target =
                new JmcFunctionalTestTarget(
                        testMethod.getName(),
                        () -> {
                            try {
                                testMethod.invoke(instance);
                            } catch (IllegalAccessException | InvocationTargetException e) {
                                throw new RuntimeException(e);
                            }
                        });
        checker.check(target);
    }
}
