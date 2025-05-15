package org.mpisws.jmc.integrations.junit5.descriptors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.annotations.JmcTimeout;
import org.mpisws.jmc.checker.JmcCheckerConfiguration;
import org.mpisws.jmc.checker.exceptions.JmcCheckerException;
import org.mpisws.jmc.integrations.junit5.engine.JmcTestExecutor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;

public class JmcMethodTestDescriptor extends AbstractTestDescriptor
        implements JmcExecutableTestDescriptor {

    private static final Logger LOGGER = LogManager.getLogger(JmcMethodTestDescriptor.class);

    private final Method testMethod;
    private final JmcCheckConfiguration parentConfigAnnotation;

    public JmcMethodTestDescriptor(Method testMethod, JmcClassTestDescriptor parent) {
        super(
                parent.getUniqueId().append("method", testMethod.getName()),
                testMethod.getName(),
                MethodSource.from(testMethod));
        this.testMethod = testMethod;
        this.parentConfigAnnotation = parent.getConfigAnnotation();
    }

    @Override
    public Type getType() {
        return Type.TEST;
    }

    private JmcCheckerConfiguration.Builder buildFromAnnotation(
            JmcCheckerConfiguration.Builder builder, JmcCheckConfiguration annotation) {
        long seed = annotation.seed();
        if (annotation.seed() == 0L) {
            seed = System.nanoTime();
        }
        return builder.numIterations(annotation.numIterations())
                .debug(annotation.debug())
                .seed(seed)
                .reportPath(annotation.reportPath())
                .strategyType(annotation.strategy());
    }

    public void execute() throws JmcCheckerException {
        LOGGER.debug("JmcMethodTestDescriptor execute() called");
        Object methodInstance;
        try {
            methodInstance = testMethod.getDeclaringClass().getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException
                | InstantiationException
                | IllegalAccessException
                | InvocationTargetException e) {
            LOGGER.error(
                    "Error creating instance of test class: {}",
                    testMethod.getDeclaringClass().getName(),
                    e);
            throw new JmcCheckerException("Error creating instance of test class", e);
        }
        testMethod.setAccessible(true);

        JmcCheckerConfiguration.Builder configBuilder = new JmcCheckerConfiguration.Builder();
        if (testMethod.getAnnotation(JmcCheckConfiguration.class) != null) {
            // Method has JmcCheckConfiguration annotation use that
            JmcCheckConfiguration annotation =
                    testMethod.getAnnotation(JmcCheckConfiguration.class);
            LOGGER.debug("JmcCheckConfiguration annotation found");
            configBuilder = buildFromAnnotation(configBuilder, annotation);
        } else if (parentConfigAnnotation != null) {
            // Class has JmcCheckConfiguration annotation use that
            JmcCheckConfiguration annotation =
                    testMethod.getDeclaringClass().getAnnotation(JmcCheckConfiguration.class);
            LOGGER.debug("JmcCheckConfiguration annotation found in class");
            configBuilder = buildFromAnnotation(configBuilder, annotation);
        } else {
            LOGGER.debug("No JmcCheckConfiguration annotation found");
            // Use default values
        }
        if (testMethod.getAnnotation(JmcTimeout.class) != null) {
            JmcTimeout annotationTimeout = testMethod.getAnnotation(JmcTimeout.class);
            configBuilder =
                    configBuilder.timeout(
                            Duration.of(annotationTimeout.value(), annotationTimeout.unit()));
        }

        configBuilder =
                JmcDescriptorUtil.checkStrategyConfig(
                        configBuilder, testMethod.getDeclaringClass(), testMethod);

        try {
            JmcCheckerConfiguration config = configBuilder.build();
            JmcTestExecutor.execute(testMethod, methodInstance, config);
        } catch (JmcCheckerException e) {
            LOGGER.error("Error executing test method: {}", testMethod.getName(), e);
            throw e;
        }
    }
}
