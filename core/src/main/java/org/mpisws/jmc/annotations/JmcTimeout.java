package org.mpisws.jmc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.temporal.ChronoUnit;

/**
 * This annotation is used to specify a timeout for a test method or class when using the JMC model
 * checker. It can be applied to methods or classes.
 *
 * <p>The timeout value is specified in the specified time unit, and if the test exceeds this
 * duration, it will be considered failed.
 *
 * <p>Either this or the {@link JmcCheckConfiguration#numIterations()} should be specified
 * mandatorily for each test
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
