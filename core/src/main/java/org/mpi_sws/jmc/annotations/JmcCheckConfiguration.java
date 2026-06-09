package org.mpi_sws.jmc.annotations;

import org.mpi_sws.jmc.strategies.trust.TrustStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;

/**
 * Configuration annotation for JMC checks.
 *
 * <p>The annotation allows users to specify parameters for the tests and is mandatory
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

    String solver() default "off";

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

    long timeout() default -1L;
}
