package org.mpisws.jmc.annotations.strategies;

import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.strategies.trust.TrustStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to configure the JMC trust strategy for a test method or class. It allows
 * specifying the scheduling policy, seed, debug mode, and report path for the trust strategy.
 *
 * <p>It can be applied to methods or classes and is equivalent to using the {@link
 * JmcCheckConfiguration#strategy()} with value "trust".
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JmcTrustStrategy {

    /** The seed for the scheduling strategy. */
    long seed() default 0;

    /**
     * The scheduling policy for the trust strategy.
     *
     * <p>- RANDOM: Randomly selects a thread to schedule. - FIFO: Selects the thread that has been
     * waiting the longest.
     */
    TrustStrategy.SchedulingPolicy schedulingPolicy() default TrustStrategy.SchedulingPolicy.RANDOM;

    /** Debug flag to enable graph logging. */
    boolean debug() default false;

    /** The path to store the execution graphs explored. */
    String reportPath() default "build/test-results/jmc-report";
}
