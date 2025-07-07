package org.mpisws.jmc.integrations.junit5.descriptors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.mpisws.jmc.annotations.*;
import org.mpisws.jmc.checker.JmcCheckerConfiguration;
import org.mpisws.jmc.checker.JmcModelCheckerReport;
import org.mpisws.jmc.checker.exceptions.JmcCheckerException;
import org.mpisws.jmc.integrations.junit5.engine.JmcTestExecutor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;

/**
 * A JUnit 5 test descriptor for a JMC method test.
 *
 * <p>This descriptor represents a single test method annotated with JMC annotations, allowing for
 * the execution of JMC checks as part of the test lifecycle.
 */
public class JmcMethodTestDescriptor extends AbstractTestDescriptor
        implements JmcExecutableTestDescriptor {

    private static final Logger LOGGER = LogManager.getLogger(JmcMethodTestDescriptor.class);

    private final Method testMethod;
    private final boolean isReplayTest;
    private final JmcCheckConfiguration parentConfigAnnotation;

    public JmcMethodTestDescriptor(Method testMethod, JmcClassTestDescriptor parent) {
        super(
                parent.getUniqueId().append("method", testMethod.getName()),
                testMethod.getName(),
                MethodSource.from(testMethod));
        this.testMethod = testMethod;
        this.isReplayTest = testMethod.getAnnotation(JmcReplay.class) != null;
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

    /**
     * Executes the JMC test method.
     *
     * <p>This method creates an instance of the test class, configures the JMC checker based on
     * annotations, and executes the test method using the JMC Model Checker.
     *
     * <p>Execution can be either running the model checker or replaying a previous execution and
     * depends on the annotation provided for the test method. If the method is annotated with
     * {@link JmcReplay}, it will replay the test method instead of executing it.
     *
     * @throws JmcCheckerException If an error occurs during execution or configuration.
     */
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
            if (isReplayTest) {
                JmcTestExecutor.executeReplay(testMethod, methodInstance, config);
            } else {
                JmcModelCheckerReport report =
                        JmcTestExecutor.execute(testMethod, methodInstance, config);
                if (testMethod.getAnnotation(JmcExpectExecutions.class) != null) {
                    JmcExpectExecutions expectExecutions =
                            testMethod.getAnnotation(JmcExpectExecutions.class);
                    if (report.getTotalIterations() != expectExecutions.value()) {
                        throw new JmcCheckerException(
                                "Expected "
                                        + expectExecutions.value()
                                        + " executions, but got "
                                        + report.getTotalIterations());
                    }
                }
            }
        } catch (JmcCheckerException e) {
            LOGGER.error("Error executing test method: {}", testMethod.getName(), e);
            throw e;
        }
    }
}
