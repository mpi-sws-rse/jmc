package org.mpi_sws.jmc.annotations;

import org.mpi_sws.jmc.strategies.trust.TrustStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;

/**
 * Marks and configures a JMC test.
 *
 * <p>Like {@link JmcCheck} it is a discovery marker, and additionally it is the <em>parameter
 * source</em> for a run: its elements (strategy, iterations, seed, timeout, ...) are copied into a
 * {@code JmcCheckerConfiguration} by the JUnit descriptors ({@code
 * JmcMethodTestDescriptor.buildFromAnnotation}, and equivalently {@code
 * JmcCheckerConfiguration.fromAnnotation}). It applies to a method or a type; a method-level
 * annotation takes precedence over a class-level one. Retained at runtime for reflective reading. A
 * test needs this (or {@link JmcCheck}) to be discovered, and a stopping condition — either {@link
 * #numIterations()} or a {@link JmcTimeout}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JmcCheckConfiguration {
    /**
     * The strategy to use for the JMC check.
     *
     * <p>Available strategies include:
     *
     * <ul>
     *   <li><code>random</code> - Randomly explores the state space.
     *   <li><code>pct</code> - Probabilistic Concurrency Testing: a priority-based randomized
     *       scheduler with a probabilistic guarantee of finding bugs of a given {@link #bugDepth()}.
     *   <li><code>fair-pct</code> - PCT followed by a fair (uniform-random) execution suffix; see
     *       {@link #pctFairBound()}.
     *   <li><code>trust</code> - Uses Trust to exhaustively enumerate all executions.
     * </ul>
     *
     * @return the strategy name
     */
    String strategy() default "random";

    /**
     * The symbolic solver to use.
     *
     * @return the solver selection ({@code "off"} disables symbolic execution)
     */
    String solver() default "off";

    /**
     * The scheduling policy for the {@code trust} strategy family.
     *
     * @return the scheduling policy ({@code RANDOM} or {@code FIFO})
     */
    TrustStrategy.SchedulingPolicy schedulingPolicy() default TrustStrategy.SchedulingPolicy.RANDOM;

    /**
     * The number of iterations to run for the JMC check.
     *
     * <p>Either this parameter or a {@link JmcTimeout} annotation should be specified for each test
     *
     * @return the number of iterations
     */
    int numIterations() default 100;

    /**
     * Enables debug logs and additional information based on the strategy used.
     *
     * @return true if debug mode is enabled, false otherwise
     */
    boolean debug() default false;

    /**
     * The path where the JMC report will be generated.
     *
     * <p>By default, the report is generated in "build/test-results/jmc-report".
     *
     * @return the report path
     */
    String reportPath() default "build/test-results/jmc-report";

    /**
     * The seed for the random number generator used in the JMC check.
     *
     * <p>By default, the seed is set to 0, which means a new random seed will be created at
     * runtime.
     *
     * @return the seed value
     */
    long seed() default 0;

    /**
     * The budget for the estimation strategy.
     *
     * @return the budget
     */
    int budget() default 2;

    /**
     * The target bug depth {@code d} for the PCT strategies (<code>pct</code>, <code>fair-pct</code>).
     *
     * <p>PCT installs {@code d - 1} priority change points and finds a bug of depth {@code d} with
     * probability at least {@code 1 / (n * k^(d-1))} per iteration. Must be at least 1; the default
     * (3) targets bugs of depth up to 3. Ignored by non-PCT strategies.
     *
     * @return the target bug depth
     */
    int bugDepth() default 3;

    /**
     * The fair-suffix bound for the <code>fair-pct</code> strategy: the number of priority-controlled
     * scheduling decisions before switching to a uniform-random ("fair") suffix.
     *
     * <p>A value {@code <= 0} (the default) selects automatic mode, in which the bound for each
     * iteration is the largest number of decisions seen in any previous run — so normal-length runs
     * stay entirely under PCT and only an abnormally long run (a spin-loop livelock) switches to the
     * fair suffix. Ignored by strategies other than <code>fair-pct</code>.
     *
     * @return the fair-suffix bound, or a non-positive value for automatic mode
     */
    int pctFairBound() default 0;

    /**
     * The wall-clock timeout for the run, in milliseconds.
     *
     * <p>A value of {@code -1} (the default) means no timeout. For a unit-aware timeout prefer the
     * {@link JmcTimeout} annotation, which overrides this value.
     *
     * @return the timeout in milliseconds, or {@code -1} for none
     */
    long timeout() default -1L;
}
