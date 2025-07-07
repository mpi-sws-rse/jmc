package org.mpisws.jmc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
     *   <li><code>trust</code> - Uses Trust to exhaustively enumerate all executions.
     * </ul>
     *
     * @return the strategy name
     */
    String strategy() default "random";

    /**
     * The number of iterations to run for the JMC check.
     *
     * <p>Either this parameter or a {@link JmcTimeout} annotation should be specified for each test
     *
     * @return the number of iterations
     */
    int numIterations() default 0;

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
}
