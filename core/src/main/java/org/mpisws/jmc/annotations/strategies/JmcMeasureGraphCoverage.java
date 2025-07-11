package org.mpisws.jmc.annotations.strategies;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * This annotation is used to configure the JMC graph coverage measurement for a test method or
 * class.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JmcMeasureGraphCoverage {
    /** Enable debug mode for the graph coverage measurement. */
    boolean debug() default false;

    /** Enable recording of the execution graphs. */
    boolean recordGraphs() default false;

    /**
     * The path where the execution graphs will be recorded.
     *
     * <p>Default is "build/test-results/jmc-coverage".
     */
    String recordPath() default "build/test-results/jmc-report";

    /**
     * The frequency at which the graph coverage will be measured.
     *
     * <p>Default is null.
     */
    ChronoUnit recordUnit() default ChronoUnit.SECONDS;

    /**
     * The frequency at which the graph coverage will be measured, in milliseconds.
     *
     * <p>Should be specified with the {@link JmcMeasureGraphCoverage#recordUnit} parameter
     *
     * <p>Default is null.
     */
    long recordFrequency() default 0L;

    /**
     * Record the graph coverage per iteration of the test. Should not be specified with
     * `recordUnit` and `recordFrequency`
     */
    boolean recordPerIteration() default false;
}
