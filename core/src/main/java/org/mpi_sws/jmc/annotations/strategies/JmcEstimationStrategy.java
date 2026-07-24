package org.mpi_sws.jmc.annotations.strategies;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Selects an {@code estimation} strategy for a test.
 *
 * <p><strong>Note:</strong> this annotation is defined but <em>not currently consumed</em> — the
 * JUnit descriptors ({@code JmcDescriptorUtil}) only react to {@link JmcTrustStrategy} and {@link
 * JmcMeasureGraphCoverage}, so applying it has no effect at present. Applicable to methods and types;
 * retained at runtime.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JmcEstimationStrategy {
    /**
     * The estimation strategy name.
     *
     * @return the strategy name (default {@code "estimation"})
     */
    String strategy() default "estimation";
}
