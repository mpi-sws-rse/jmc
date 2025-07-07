package org.mpisws.jmc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to mark a test method or class to expect a certain number of executions
 * in the JMC model checker. It can be applied to methods or classes.
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
