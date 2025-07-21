package org.mpisws.jmc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to mark a test method or class to expect an assertion failure in the JMC
 * model checker. It can be applied to methods or classes.
 *
 * <p>When this annotation is present, the JMC model checker will expect an assertion failure during
 * the execution of the annotated test method or class.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JmcExpectAssertionFailure {}
