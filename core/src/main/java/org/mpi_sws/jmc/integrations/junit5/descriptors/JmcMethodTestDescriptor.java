package org.mpi_sws.jmc.integrations.junit5.descriptors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.mpi_sws.jmc.annotations.*;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException;
import org.mpi_sws.jmc.integrations.junit5.engine.JmcTestExecutor;
import org.mpi_sws.jmc.util.ExceptionUtil;

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

    /** Logger for per-test execution diagnostics. */
    private static final Logger LOGGER = LogManager.getLogger(JmcMethodTestDescriptor.class);

    /** The test method this descriptor runs through the model checker. */
    private final Method testMethod;
    /** Whether the method is annotated with {@link JmcReplay} (replay a trace instead of exploring). */
    private final boolean isReplayTest;
    /** The enclosing class's {@link JmcCheckConfiguration} (from the parent), used as a fallback config. */
    private final JmcCheckConfiguration parentConfigAnnotation;

    /**
     * Builds a method descriptor for {@code testMethod} under its class descriptor.
     *
     * <p>Sets the unique id (parent's id + {@code ("method", methodName)}), the method name as the
     * display name, and a {@link MethodSource}. Records whether the method is a {@link JmcReplay} test
     * and captures the parent's class-level configuration for use as a fallback in {@link #execute()}.
     *
     * @param testMethod the test method to describe
     * @param parent the enclosing {@link JmcClassTestDescriptor}
     */
    public JmcMethodTestDescriptor(Method testMethod, JmcClassTestDescriptor parent) {
        super(
                parent.getUniqueId().append("method", testMethod.getName()),
                testMethod.getName(),
                MethodSource.from(testMethod));
        this.testMethod = testMethod;
        this.isReplayTest = testMethod.getAnnotation(JmcReplay.class) != null;
        this.parentConfigAnnotation = parent.getConfigAnnotation();
    }

    /**
     * @return {@link Type#TEST} — this is a runnable leaf, not a container
     */
    @Override
    public Type getType() {
        return Type.TEST;
    }

    /**
     * Copies the fields of a {@link JmcCheckConfiguration} annotation into a checker-configuration
     * builder.
     *
     * <p>Transfers {@code numIterations}, {@code debug}, {@code seed}, {@code budget}, {@code timeout},
     * {@code reportPath}, {@code strategy}, {@code solver}, and {@code schedulingPolicy}. A {@code
     * seed} of {@code 0} is replaced with {@code System.nanoTime()} so each run gets a fresh seed.
     *
     * @param builder the configuration builder to populate
     * @param annotation the source {@link JmcCheckConfiguration} (from the method or the class)
     * @return the same builder, updated from the annotation
     */
    private JmcCheckerConfiguration.Builder buildFromAnnotation(
            JmcCheckerConfiguration.Builder builder, JmcCheckConfiguration annotation) {
        long seed = annotation.seed();
        int budget = annotation.budget();
        long timeout = annotation.timeout();
        if (annotation.seed() == 0L) {
            seed = System.nanoTime();
        }
        return builder.numIterations(annotation.numIterations())
                .debug(annotation.debug())
                .seed(seed)
                .budget(budget)
                .timeout(timeout)
                .reportPath(annotation.reportPath())
                .strategyType(annotation.strategy())
                .solver(annotation.solver())
                .schedulingPolicy(annotation.schedulingPolicy());
    }

    /**
     * Executes the JMC test method.
     *
     * <p>This method creates an instance of the test class, configures the JMC checker based on
     * annotations, and executes the test method using the JMC Model Checker.
     *
     * <p>Configuration is resolved with precedence method ▷ class ▷ defaults: a method-level {@link
     * JmcCheckConfiguration} is used if present, otherwise the class-level one (via {@link
     * #parentConfigAnnotation}), otherwise built-in defaults (see {@link #buildFromAnnotation}). A
     * {@link JmcTimeout} annotation then overrides the timeout, and {@link
     * JmcDescriptorUtil#checkStrategyConfig} applies any strategy annotations.
     *
     * <p>Execution can be either running the model checker or replaying a previous execution and
     * depends on the annotation provided for the test method. If the method is annotated with
     * {@link JmcReplay}, it will replay the test method instead of executing it via {@link
     * JmcTestExecutor}. For a normal run, if the method is annotated with {@link JmcExpectExecutions}
     * the number of completed iterations ({@code totalIterations − blockedIterations}) is asserted to
     * match the expected value.
     *
     * <p>If the method is annotated with {@link JmcExpectAssertionFailure}, a {@link
     * JmcCheckerException} whose cause is an {@code AssertionError} is treated as success; a test
     * expected to fail that instead passes is turned into a {@link JmcCheckerException}.
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

        boolean expectFailure = testMethod.getAnnotation(JmcExpectAssertionFailure.class) != null;
        boolean failed = false;
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
                    int completeIteration = report.getTotalIterations() - report.getBlockedIterations();
                    if (completeIteration != expectExecutions.value()) {
                        throw new JmcCheckerException(
                                "Expected "
                                        + expectExecutions.value()
                                        + " executions, but got "
                                        + completeIteration);
                    }
                }
            }
        } catch (JmcCheckerException e) {
            if (expectFailure && ExceptionUtil.isAssertionError(e.getCause())) {
                failed = true;
            } else {
                LOGGER.error("Error executing test method: {}", testMethod.getName(), e);
                throw e;
            }
        }
        if (expectFailure && !failed) {
            throw new JmcCheckerException(
                    "Test method "
                            + testMethod.getName()
                            + " expected to fail but passed successfully.");
        }
    }
}
