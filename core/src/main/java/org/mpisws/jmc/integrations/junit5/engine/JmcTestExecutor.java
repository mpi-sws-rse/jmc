package org.mpisws.jmc.integrations.junit5.engine;

import org.mpisws.jmc.checker.JmcCheckerConfiguration;
import org.mpisws.jmc.checker.JmcFunctionalTestTarget;
import org.mpisws.jmc.checker.JmcModelChecker;
import org.mpisws.jmc.checker.JmcTestTarget;
import org.mpisws.jmc.checker.exceptions.JmcCheckerException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class JmcTestExecutor {

    public static void execute(Method testMethod, Object instance, JmcCheckerConfiguration config) throws JmcCheckerException {
        System.out.println("JmcTestExecutor Executing test: " + testMethod.getName());
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

    public static void executeReplay(Method testMethod, Object instance, JmcCheckerConfiguration config, Long seed, int iteration) throws JmcCheckerException {
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
        checker.replay(target, seed, iteration);
    }
}
