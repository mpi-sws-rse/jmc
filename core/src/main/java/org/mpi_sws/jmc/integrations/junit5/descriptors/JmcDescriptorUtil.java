package org.mpi_sws.jmc.integrations.junit5.descriptors;

import org.mpi_sws.jmc.annotations.strategies.JmcMeasureGraphCoverage;
import org.mpi_sws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.exceptions.JmcInvalidConfigurationException;
import org.mpi_sws.jmc.strategies.SchedulingStrategyConfiguration;
import org.mpi_sws.jmc.strategies.trust.MeasureGraphCoverageStrategy;
import org.mpi_sws.jmc.strategies.trust.MeasureGraphCoverageStrategyConfig;
import org.mpi_sws.jmc.strategies.trust.TrustStrategy;

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
     * @param clazz   The class to check for annotations.
     * @param method  The method to check for annotations.
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

    /**
     * @param method the method to inspect
     * @return {@code true} if the method carries {@link JmcTrustStrategy}
     */
    private static boolean hasStrategyAnnotation(Method method) {
        return method.isAnnotationPresent(JmcTrustStrategy.class);
    }

    /**
     * @param clazz the class to inspect
     * @return {@code true} if the class carries {@link JmcTrustStrategy}
     */
    private static boolean hasStrategyAnnotation(Class<?> clazz) {
        return clazz.isAnnotationPresent(JmcTrustStrategy.class);
    }

    /**
     * Installs a {@code trust}-strategy constructor on the builder from a method's {@link
     * JmcTrustStrategy}.
     *
     * <p>Builds the base strategy constructor via {@link #getStrategyConstructor}; if the method also
     * carries {@link JmcMeasureGraphCoverage}, wraps it with a coverage-measuring constructor (see
     * {@link #getCoverageStrategyConstructor}). Sets the result as the builder's strategy constructor.
     *
     * @param builder the configuration builder to update
     * @param method the method carrying {@link JmcTrustStrategy}
     * @return the updated builder
     * @throws JmcInvalidConfigurationException if the coverage annotation is misconfigured
     */
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

    /**
     * Class-level counterpart of {@link #updateBuilderFromAnnotation(JmcCheckerConfiguration.Builder,
     * Method)}: installs a {@code trust}-strategy constructor from a class's {@link JmcTrustStrategy},
     * optionally wrapped for {@link JmcMeasureGraphCoverage}.
     *
     * @param builder the configuration builder to update
     * @param clazz the class carrying {@link JmcTrustStrategy}
     * @return the updated builder
     * @throws JmcInvalidConfigurationException if the coverage annotation is misconfigured
     */
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

    /**
     * Wraps a base strategy constructor with graph-coverage measurement configured from a {@link
     * JmcMeasureGraphCoverage} annotation.
     *
     * <p>Returns a constructor that, given a config, builds a {@link MeasureGraphCoverageStrategy}
     * around the base strategy — honoring the annotation's {@code debug}, {@code recordGraphs}, {@code
     * recordPath}, and either a recording {@code frequency} or per-iteration recording. Rejects the
     * invalid combination of both a non-zero {@code recordFrequency} and {@code recordPerIteration}.
     *
     * @param coverageAnnotation the coverage annotation to read settings from
     * @param constructor the base strategy constructor to wrap
     * @return a strategy constructor that adds coverage measurement
     * @throws JmcInvalidConfigurationException if both {@code recordFrequency} and {@code
     *     recordPerIteration} are set
     */
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

    /**
     * Builds a strategy constructor that creates a {@link TrustStrategy} from a {@link
     * JmcTrustStrategy} annotation.
     *
     * <p>The returned constructor uses the annotation's seed, or the checker config's seed when the
     * annotation's seed is {@code 0}, together with the annotation's scheduling policy, debug flag,
     * report path, logger-tree flag, and solver.
     *
     * @param annotation the {@link JmcTrustStrategy} annotation to read settings from
     * @return a strategy constructor producing a {@link TrustStrategy}
     */
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
                    annotation.reportPath(),
                    annotation.loggerTree(),
                    annotation.solver());
        };
    }
}
