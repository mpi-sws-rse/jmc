package org.mpi_sws.jmc.annotations.strategies;

import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.strategies.trust.TrustStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Selects and configures the JMC {@code trust} strategy for a test.
 *
 * <p>It is equivalent to {@link JmcCheckConfiguration#strategy()} {@code = "trust"} but exposes
 * trust-specific options. It is consumed by {@code JmcDescriptorUtil.checkStrategyConfig}, which reads
 * it (method first, then class) and installs an explicit strategy constructor building a {@code
 * TrustStrategy} from these elements — taking precedence over the {@code strategy} string. Applicable
 * to methods and types; retained at runtime.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JmcTrustStrategy {

    /**
     * The seed for the scheduling strategy.
     *
     * @return the seed; {@code 0} (the default) falls back to the checker configuration's seed
     */
    long seed() default 0;

    /**
     * The scheduling policy for the trust strategy: {@code RANDOM} selects a thread at random, {@code
     * FIFO} selects the thread that has been waiting the longest.
     *
     * @return the scheduling policy
     */
    TrustStrategy.SchedulingPolicy schedulingPolicy() default TrustStrategy.SchedulingPolicy.RANDOM;

    /**
     * Debug flag to enable execution-graph logging.
     *
     * @return {@code true} to enable graph logging
     */
    boolean debug() default false;

    /**
     * The path to store the explored execution graphs.
     *
     * @return the report path
     */
    String reportPath() default "build/test-results/jmc-report";

    /**
     * Whether to log the exploration as a tree.
     *
     * @return {@code true} to enable logger-tree output
     */
    boolean loggerTree() default false;

    /**
     * The symbolic solver to use.
     *
     * @return the solver selection ({@code "off"} disables symbolic execution)
     */
    String solver() default "off";
}
