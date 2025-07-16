package org.mpisws.jmc.integrations.junit5.descriptors;

import org.mpisws.jmc.annotations.strategies.JmcMeasureGraphCoverage;
import org.mpisws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpisws.jmc.checker.JmcCheckerConfiguration;
import org.mpisws.jmc.checker.exceptions.JmcInvalidConfigurationException;
import org.mpisws.jmc.strategies.SchedulingStrategyConfiguration;
import org.mpisws.jmc.strategies.trust.MeasureGraphCoverageStrategy;
import org.mpisws.jmc.strategies.trust.MeasureGraphCoverageStrategyConfig;
import org.mpisws.jmc.strategies.trust.TrustStrategy;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * Utility class for handling JMC descriptor configurations.
 *
 * <p>This class provides methods to check and update JMC checker configurations based on
 * annotations present on classes or methods.
 */
public class JmcDescriptorUtil {

    /**
     * Checks the provided class and method for JMC trust strategy annotations and updates the JMC
     * checker configuration builder accordingly.
     *
     * @param builder The JMC checker configuration builder to update.
     * @param clazz The class to check for annotations.
     * @param method The method to check for annotations.
     * @return An updated JMC checker configuration builder.
     */
    public static JmcCheckerConfiguration.Builder checkStrategyConfig(
            JmcCheckerConfiguration.Builder builder, Class<?> clazz, Method method)
            throws JmcInvalidConfigurationException {
        if (method != null && hasStrategyAnnotation(method)) {
            return updateBuilderFromAnnotation(builder, method);
        } else if (clazz != null && hasStrategyAnnotation(clazz)) {
            return updateBuilderFromAnnotation(builder, clazz);
        } else {
            return builder;
        }
    }

    private static boolean hasStrategyAnnotation(Method method) {
        return method.isAnnotationPresent(JmcTrustStrategy.class);
    }

    private static boolean hasStrategyAnnotation(Class<?> clazz) {
        return clazz.isAnnotationPresent(JmcTrustStrategy.class);
    }

    private static JmcCheckerConfiguration.Builder updateBuilderFromAnnotation(
            JmcCheckerConfiguration.Builder builder, Method method)
            throws JmcInvalidConfigurationException {
        JmcTrustStrategy annotation = method.getAnnotation(JmcTrustStrategy.class);
        SchedulingStrategyConfiguration.SchedulingStrategyConstructor constructor =
                getStrategyConstructor(annotation);

        if (method.getAnnotation(JmcMeasureGraphCoverage.class) != null) {
            JmcMeasureGraphCoverage coverageAnnotation =
                    method.getAnnotation(JmcMeasureGraphCoverage.class);
            SchedulingStrategyConfiguration.SchedulingStrategyConstructor measureConstructor =
                    getCoverageStrategyConstructor(coverageAnnotation, constructor);
            return builder.strategyConstructor(measureConstructor);
        } else {
            return builder.strategyConstructor(constructor);
        }
    }

    private static JmcCheckerConfiguration.Builder updateBuilderFromAnnotation(
            JmcCheckerConfiguration.Builder builder, Class<?> clazz)
            throws JmcInvalidConfigurationException {
        JmcTrustStrategy annotation = clazz.getAnnotation(JmcTrustStrategy.class);
        SchedulingStrategyConfiguration.SchedulingStrategyConstructor constructor =
                getStrategyConstructor(annotation);

        if (clazz.getAnnotation(JmcMeasureGraphCoverage.class) != null) {
            JmcMeasureGraphCoverage coverageAnnotation =
                    clazz.getAnnotation(JmcMeasureGraphCoverage.class);
            SchedulingStrategyConfiguration.SchedulingStrategyConstructor measureConstructor =
                    getCoverageStrategyConstructor(coverageAnnotation, constructor);
            return builder.strategyConstructor(measureConstructor);
        } else {
            return builder.strategyConstructor(constructor);
        }
    }

    private static SchedulingStrategyConfiguration.SchedulingStrategyConstructor
            getCoverageStrategyConstructor(
                    JmcMeasureGraphCoverage coverageAnnotation,
                    SchedulingStrategyConfiguration.SchedulingStrategyConstructor constructor)
                    throws JmcInvalidConfigurationException {
        if (coverageAnnotation.recordFrequency() != 0L && coverageAnnotation.recordPerIteration()) {
            throw new JmcInvalidConfigurationException(
                    "Cannot set both recordFrequency and recordPerIteration to true in JmcMeasureGraphCoverage annotation.");
        }
        return (config) -> {
            Duration frequency =
                    Duration.of(
                            coverageAnnotation.recordFrequency(), coverageAnnotation.recordUnit());
            MeasureGraphCoverageStrategyConfig.MeasureGraphCoverageStrategyConfigBuilder builder =
                    MeasureGraphCoverageStrategyConfig.builder()
                            .debug(coverageAnnotation.debug())
                            .recordGraphs(coverageAnnotation.recordGraphs())
                            .recordPath(coverageAnnotation.recordPath());
            if (coverageAnnotation.recordFrequency() != 0L) {
                builder.withFrequency(frequency);
            } else if (coverageAnnotation.recordPerIteration()) {
                builder.recordPerIteration();
            }

            return new MeasureGraphCoverageStrategy(constructor.create(config), builder.build());
        };
    }

    private static SchedulingStrategyConfiguration.SchedulingStrategyConstructor
            getStrategyConstructor(JmcTrustStrategy annotation) {
        return (config) -> {
            long seed = config.getSeed();
            if (annotation.seed() != 0L) {
                seed = annotation.seed();
            }
            return new TrustStrategy(
                    seed,
                    annotation.schedulingPolicy(),
                    annotation.debug(),
                    annotation.reportPath());
        };
    }
}
