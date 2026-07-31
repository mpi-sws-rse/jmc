package org.mpi_sws.jmc.annotations.strategies;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.temporal.ChronoUnit;

/**
 * Enables graph-coverage measurement for a JMC test.
 *
 * <p>Applied alongside a strategy, it is consumed by {@code JmcDescriptorUtil}, which wraps the chosen
 * strategy in a {@code MeasureGraphCoverageStrategy} configured from these elements — counting how
 * many execution graphs are covered, recorded either at a fixed frequency or per iteration. Setting
 * both a non-zero {@link #recordFrequency()} and {@link #recordPerIteration()} is rejected with a
 * {@code JmcInvalidConfigurationException}. Applicable to methods and types; retained at runtime.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JmcMeasureGraphCoverage {
    /**
     * Enable debug mode for the graph coverage measurement.
     *
     * @return {@code true} to enable debug mode
     */
    boolean debug() default false;

    /**
     * Enable recording of the execution graphs.
     *
     * @return {@code true} to record the explored graphs
     */
    boolean recordGraphs() default false;

    /**
     * The path where the execution graphs will be recorded.
     *
     * @return the record path (default {@code "build/test-results/jmc-report"})
     */
    String recordPath() default "build/test-results/jmc-report";

    /**
     * The time unit paired with {@link #recordFrequency()} to form the recording interval (combined as
     * {@code Duration.of(recordFrequency, recordUnit)}).
     *
     * @return the time unit for the recording frequency (default {@code SECONDS})
     */
    ChronoUnit recordUnit() default ChronoUnit.SECONDS;

    /**
     * The recording interval magnitude, in units of {@link #recordUnit()}.
     *
     * <p>{@code 0} (the default) disables frequency-based recording. Mutually exclusive with {@link
     * #recordPerIteration()}.
     *
     * @return the recording frequency, or {@code 0} for none
     */
    long recordFrequency() default 0L;

    /**
     * Record the graph coverage once per test iteration. Mutually exclusive with {@link
     * #recordUnit()}/{@link #recordFrequency()}.
     *
     * @return {@code true} to record per iteration
     */
    boolean recordPerIteration() default false;
}
