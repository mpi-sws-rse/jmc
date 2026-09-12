package org.mpi_sws.jmc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Asserts the number of executions a JMC test should explore.
 *
 * <p>Consumed by {@code JmcMethodTestDescriptor.execute} after a (non-replay) run: it compares the
 * number of <em>completed</em> iterations ({@code totalIterations − blockedIterations}) against {@link
 * #value()} and fails the test if they differ. Most useful with the {@code trust} strategy, whose
 * explored-execution count is optimal. Retained at runtime; applicable to methods and types.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JmcExpectExecutions {
    /**
     * The expected number of executions for the annotated test method or class.
     *
     * <p>This value is used to verify that the JMC model checker produces the expected number of
     * executions during the test run.
     *
     * @return the expected number of executions
     */
    int value();
}
