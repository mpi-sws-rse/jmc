package org.mpisws.jmc.integrations.junit5.descriptors;

import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.checker.JmcCheckerConfiguration;
import org.mpisws.jmc.checker.JmcFunctionalTestTarget;
import org.mpisws.jmc.checker.JmcModelChecker;
import org.mpisws.jmc.checker.JmcTestTarget;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class JmcMethodTestDescriptor extends AbstractTestDescriptor {

    private final Method testMethod;

    protected JmcMethodTestDescriptor(UniqueId uniqueId, Class<?> testClass, Method testMethod) {
        super(uniqueId, JmcEngineDescriptor.ENGINE_DISPLAY_NAME);
        this.testMethod = testMethod;
    }

    public JmcMethodTestDescriptor(Method testMethod, JmcClassTestDescriptor parent) {
        super(
                parent.getUniqueId().append("method", testMethod.getName()),
                testMethod.getName(),
                MethodSource.from(testMethod.getDeclaringClass(), testMethod)
        );
        this.testMethod = testMethod;
        setParent(parent);
    }

    @Override
    public Type getType() {
        return Type.TEST;
    }

    public void execute() throws Exception {
        Object instance = testMethod.getDeclaringClass().getDeclaredConstructor().newInstance();
        testMethod.setAccessible(true);

        if (testMethod.isAnnotationPresent(JmcCheckConfiguration.class)) {
        JmcCheckConfiguration annotation = testMethod.getAnnotation(JmcCheckConfiguration.class);

        JmcCheckerConfiguration config = new JmcCheckerConfiguration.Builder()
                .numIterations(annotation.numIterations())
                .debug(annotation.debug())
                .seed(annotation.seed())
                .reportPath(annotation.reportPath())
                .strategyType(annotation.strategy())
                .build();
        JmcModelChecker checker = new JmcModelChecker(config);
        JmcTestTarget target = new JmcFunctionalTestTarget(
                testMethod.getName(),
                () -> {
                    try {
                        testMethod.invoke(instance);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                }
        );
        checker.check(target);

        } else {
            testMethod.invoke(instance);
        }
    }
}
