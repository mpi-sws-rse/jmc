package org.mpi_sws.jmc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.temporal.ChronoUnit;

/**
 * Specifies a wall-clock timeout for a JMC test.
 *
 * <p>The timeout is given as a {@link #value()} in a {@link #unit()}; a run that exceeds this
 * duration is stopped. It is consumed by {@code JmcMethodTestDescriptor.execute}, which reads it from
 * the test method and overrides the timeout on the {@code JmcCheckerConfiguration} (as {@code
 * Duration.of(value, unit)}), taking precedence over {@link JmcCheckConfiguration#timeout()}.
 *
 * <p>Either this or the {@link JmcCheckConfiguration#numIterations()} should be specified as the
 * run's stopping condition. Retained at runtime; applicable to methods and types.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JmcTimeout {
    /**
     * The timeout value for the annotated test method or class.
     *
     * <p>This value is used to determine how long the test should run before it is considered
     * failed due to timeout.
     *
     * @return the timeout value
     */
    long value();

    /**
     * The time unit for the timeout value.
     *
     * <p>This specifies the unit of time for the timeout value, such as seconds, milliseconds, etc.
     *
     * @return the time unit for the timeout
     */
    ChronoUnit unit() default ChronoUnit.SECONDS;
}
